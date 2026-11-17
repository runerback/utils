import { useEffect, useRef, useState } from 'react';
import {
  cancelBuild,
  createPlan,
  loadGraph as requestGraph,
  startBootstrap,
  startBuild
} from './api/client';
import { BuildStatusList } from './components/BuildStatusList';
import { ContextForm } from './components/ContextForm';
import { FailureDiagnostics } from './components/FailureDiagnostics';
import { LogPanel } from './components/LogPanel';
import { PlanPanel } from './components/PlanPanel';
import { ProjectTree } from './components/ProjectTree';
import { useBuildRealtime } from './hooks/useBuildRealtime';
import { useGraphLoadProgress } from './hooks/useGraphLoadProgress';
import { getSavedRootProject, saveRootProject } from './utils/rootProjectStorage';
import { getProjectSettings, saveProjectSettings } from './utils/projectSettingsStorage';
import type {
  BuildContextOptions,
  BuildLogEvent,
  BuildPlan,
  BuildSessionSnapshot,
  GraphResponse,
  ProjectBuildStatusChangedEvent
} from './types';
import './styles.css';

const initialContext: BuildContextOptions = {
  rootProjectPath: '',
  configuration: 'Debug',
  targetFramework: 'net8.0',
  runtimeIdentifier: null,
  platform: null,
  restoreBeforeBuild: true,
  additionalProperties: {}
};

function isBuildActive(session: BuildSessionSnapshot | null): boolean {
  return session?.state === 'Pending' || session?.state === 'Running';
}

function isTerminalProjectState(state: string): boolean {
  return state === 'Succeeded' || state === 'Failed' || state === 'Skipped' || state === 'Cancelled';
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export default function App() {
  const [context, setContext] = useState<BuildContextOptions>(() => {
    const savedRoot = getSavedRootProject();
    const savedSettings = savedRoot ? getProjectSettings(savedRoot) : null;

    return {
      ...initialContext,
      ...savedSettings,
      rootProjectPath: savedRoot ?? ''
    };
  });
  const [graph, setGraph] = useState<GraphResponse | null>(null);
  const [selectedProjects, setSelectedProjects] = useState<Set<string>>(new Set());
  const [plan, setPlan] = useState<BuildPlan | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [outputsValidated, setOutputsValidated] = useState(false);
  const [session, setSession] = useState<BuildSessionSnapshot | null>(null);
  const [activeBuildId, setActiveBuildId] = useState<string | null>(null);
  const [logs, setLogs] = useState<BuildLogEvent[]>([]);
  const [loadingGraph, setLoadingGraph] = useState(false);
  const [planning, setPlanning] = useState(false);
  const [startingBuild, setStartingBuild] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const planRequestSequence = useRef(0);

  const buildActive = isBuildActive(session);
  const controlsLocked = buildActive || planning || startingBuild;
  const {
    progress: graphLoadProgress,
    runWithProgress,
    clearProgress
  } = useGraphLoadProgress();

  const connectionState = useBuildRealtime(activeBuildId, {
    onSnapshot: snapshot => {
      setSession(snapshot);

      if (snapshot.plan) {
        setPlan(snapshot.plan);
      }
    },
    onLog: log => {
      setLogs(current => [...current.slice(-4999), log]);
    },
    onProjectStatus: (status: ProjectBuildStatusChangedEvent) => {
      setSession(current => {
        if (!current) {
          return current;
        }

        const existingIndex = current.projects.findIndex(
          project => project.projectPath.toLowerCase() === status.projectPath.toLowerCase());

        if (existingIndex < 0) {
          return {
            ...current,
            projects: [
              ...current.projects,
              {
                projectPath: status.projectPath,
                projectName: status.projectName,
                state: status.state,
                message: status.message,
                startedAt: status.state === 'Building' ? status.timestamp : null,
                completedAt: isTerminalProjectState(status.state) ? status.timestamp : null
              }
            ]
          };
        }

        const projects = [...current.projects];
        projects[existingIndex] = {
          ...projects[existingIndex],
          state: status.state,
          message: status.message,
          startedAt: status.state === 'Building'
            ? projects[existingIndex].startedAt ?? status.timestamp
            : projects[existingIndex].startedAt,
          completedAt: isTerminalProjectState(status.state)
            ? projects[existingIndex].completedAt ?? status.timestamp
            : projects[existingIndex].completedAt
        };

        return { ...current, projects };
      });
    }
  });

  const updateContext = (nextContext: BuildContextOptions) => {
    planRequestSequence.current += 1;
    setPlanning(false);

    const rootChanged = nextContext.rootProjectPath.trim().toLowerCase() !==
      context.rootProjectPath.trim().toLowerCase();
    let resolvedContext = nextContext;

    if (rootChanged) {
      const savedSettings = getProjectSettings(nextContext.rootProjectPath);

      if (savedSettings) {
        resolvedContext = savedSettings;
      }
    }

    setContext(resolvedContext);
    saveProjectSettings(resolvedContext);
    setGraph(null);
    setSelectedProjects(new Set());
    setPlan(null);
    setValidationErrors([]);
    setOutputsValidated(false);
    setActionError(null);
  };

  const previewPlan = async (validateOutputs: boolean) => {
    if (!graph) {
      return;
    }

    const requestId = ++planRequestSequence.current;
    setPlanning(true);
    setActionError(null);

    try {
      const response = await createPlan(
        context,
        [...selectedProjects],
        validateOutputs);

      if (requestId !== planRequestSequence.current) {
        return;
      }

      setPlan(response.plan);
      setValidationErrors(response.validationErrors);
      setOutputsValidated(validateOutputs && response.validationErrors.length === 0);
    }
    catch (error) {
      if (requestId !== planRequestSequence.current) {
        return;
      }

      setPlan(null);
      setValidationErrors([]);
      setOutputsValidated(false);
      setActionError(errorMessage(error));
    }
    finally {
      if (requestId === planRequestSequence.current) {
        setPlanning(false);
      }
    }
  };

  useEffect(() => {
    if (!graph || buildActive || startingBuild) {
      return;
    }

    setPlanning(true);

    const timer = window.setTimeout(() => {
      void previewPlan(false);
    }, 250);

    return () => window.clearTimeout(timer);
  }, [graph, selectedProjects, context, buildActive]);

  const loadProjectGraph = async () => {
    setLoadingGraph(true);
    setActionError(null);
    setValidationErrors([]);
    setOutputsValidated(false);

    try {
      const loadedGraph = await runWithProgress(progressId => requestGraph(context, progressId));
      setPlanning(true);

      const canonicalContext = {
        ...context,
        rootProjectPath: loadedGraph.rootProjectPath
      };

      setContext(canonicalContext);
      setGraph(loadedGraph);
      saveRootProject(loadedGraph.rootProjectPath);
      saveProjectSettings(canonicalContext);
      setSelectedProjects(new Set());
      setPlan(null);
      setOutputsValidated(false);
      window.setTimeout(clearProgress, 1200);
    }
    catch (error) {
      setGraph(null);
      setActionError(errorMessage(error));
      window.setTimeout(clearProgress, 2500);
    }
    finally {
      setLoadingGraph(false);
    }
  };

  const toggleProject = (projectPath: string, checked: boolean) => {
    setOutputsValidated(false);
    setSelectedProjects(current => {
      const next = new Set(current);

      if (checked) {
        next.add(projectPath);
      }
      else {
        next.delete(projectPath);
      }

      return next;
    });
  };

  const runPartialBuild = async () => {
    if (startingBuild || buildActive) {
      return;
    }

    setStartingBuild(true);
    setActionError(null);
    setValidationErrors([]);

    try {
      const started = await startBuild(context, [...selectedProjects]);
      setSession(started);
      setActiveBuildId(started.id);
      setLogs(started.recentLogs);

      if (started.plan) {
        setPlan(started.plan);
      }

      setOutputsValidated(true);
    }
    catch (error) {
      const errors = error instanceof Error && 'validationErrors' in error
        ? (error as { validationErrors?: string[] }).validationErrors ?? []
        : [];

      if (errors.length > 0) {
        setValidationErrors(errors);
        setOutputsValidated(false);
        setActionError(null);
      }
      else {
        setActionError(errorMessage(error));
      }
    }
    finally {
      setStartingBuild(false);
    }
  };

  const runBootstrap = async () => {
    if (startingBuild || buildActive) {
      return;
    }

    setStartingBuild(true);
    setActionError(null);
    setValidationErrors([]);

    try {
      const started = await startBootstrap(context);
      setSession(started);
      setActiveBuildId(started.id);
      setLogs(started.recentLogs);
    }
    catch (error) {
      setActionError(errorMessage(error));
    }
    finally {
      setStartingBuild(false);
    }
  };

  const cancelCurrentBuild = async () => {
    if (!activeBuildId) {
      return;
    }

    try {
      await cancelBuild(activeBuildId);
    }
    catch (error) {
      setActionError(errorMessage(error));
    }
  };

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>.NET Partial Builder</h1>
          <p>Rebuild only selected projects and their required dependents.</p>
        </div>
        {session && (
          <span className={`build-state ${session.state.toLowerCase()}`}>
            {session.state}
          </span>
        )}
      </header>

      {actionError && (
        <div className="global-error">
          {actionError}
        </div>
      )}

      <main>
        <div className="top-layout">
          <ContextForm
            context={context}
            disabled={controlsLocked}
            loading={loadingGraph}
            graphProgress={graphLoadProgress}
            onChange={updateContext}
            onLoadGraph={loadProjectGraph} />

          <PlanPanel
            plan={plan}
            validationErrors={validationErrors}
            outputsValidated={outputsValidated}
            planning={planning}
            starting={startingBuild}
            session={session}
            onValidatePlan={() => void previewPlan(true)}
            onStartBuild={runPartialBuild}
            onBootstrap={runBootstrap}
            onCancel={cancelCurrentBuild} />
        </div>

        <div className="workspace-layout">
          <ProjectTree
            graph={graph}
            selectedProjects={selectedProjects}
            plan={plan}
            session={session}
            disabled={controlsLocked}
            onToggleProject={toggleProject} />

          <div className="side-layout">
            <BuildStatusList session={session} />
            <FailureDiagnostics session={session} logs={logs} />
            <LogPanel
              logs={logs}
              session={session}
              connectionState={connectionState}
              onClear={() => setLogs([])} />
          </div>
        </div>
      </main>
    </div>
  );
}
