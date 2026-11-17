import { useMemo, useState } from 'react';
import type { BuildLogEvent, BuildSessionSnapshot } from '../types';
import { isErrorLog } from '../utils/logFilters';

interface FailureDiagnosticsProps {
  session: BuildSessionSnapshot | null;
  logs: BuildLogEvent[];
}

function formatContext(session: BuildSessionSnapshot): string[] {
  const context = session.context;

  return [
    `Root: ${context.rootProjectPath}`,
    `Configuration: ${context.configuration}`,
    `Target framework: ${context.targetFramework ?? '<all>'}`,
    `Runtime identifier: ${context.runtimeIdentifier ?? '<none>'}`,
    `Platform: ${context.platform ?? '<default>'}`,
    `Restore before build: ${context.restoreBeforeBuild}`,
    ...Object.entries(context.additionalProperties).map(([key, value]) => `${key}: ${value}`)
  ];
}

export function FailureDiagnostics({ session, logs }: FailureDiagnosticsProps) {
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');

  const failedProjects = useMemo(() => {
    return session?.projects.filter(project =>
      project.state === 'Failed' || project.state === 'Cancelled') ?? [];
  }, [session]);

  const skippedProjects = useMemo(() => {
    return session?.projects.filter(project => project.state === 'Skipped') ?? [];
  }, [session]);

  const recentErrors = useMemo(() => {
    return logs.filter(isErrorLog).slice(-12);
  }, [logs]);

  if (!session || (session.state !== 'Failed' && session.state !== 'Cancelled')) {
    return null;
  }

  const createReport = () => {
    const lines = [
      '# .NET Partial Builder diagnostics',
      '',
      `Build ID: ${session.id}`,
      `State: ${session.state}`,
      `Bootstrap: ${session.isBootstrap}`,
      `Started: ${session.startedAt ?? '<unknown>'}`,
      `Completed: ${session.completedAt ?? '<unknown>'}`,
      '',
      '## Context',
      ...formatContext(session),
      '',
      '## Failure',
      session.errorMessage ?? '<none>',
      '',
      '## Failed or cancelled projects',
      ...(failedProjects.length > 0
        ? failedProjects.map(project => `- ${project.projectName}: ${project.message ?? project.projectPath}`)
        : ['<none>']),
      '',
      '## Skipped projects',
      ...(skippedProjects.length > 0
        ? skippedProjects.map(project => `- ${project.projectName}: ${project.message ?? project.projectPath}`)
        : ['<none>']),
      '',
      '## Recent error lines',
      ...(recentErrors.length > 0
        ? recentErrors.map(log => `[${log.timestamp}] [${log.projectName ?? 'build'}] ${log.message}`)
        : ['<none captured>']),
      '',
      `Complete log: ${session.logFilePath ?? '<not available>'}`
    ];

    return lines.join('\n');
  };

  const copyReport = async () => {
    try {
      await navigator.clipboard.writeText(createReport());
      setCopyState('copied');
    }
    catch {
      setCopyState('failed');
    }

    window.setTimeout(() => setCopyState('idle'), 1800);
  };

  return (
    <section className="panel diagnostics-panel">
      <div className="panel-header compact">
        <div>
          <h2>Failure diagnostics</h2>
          <p>Information to include when reporting this build failure.</p>
        </div>
        <span className={`status-badge ${session.state.toLowerCase()}`}>{session.state}</span>
      </div>

      <div className="diagnostics-summary">
        <div><strong>Build ID</strong><code>{session.id}</code></div>
        <div><strong>Root</strong><code>{session.context.rootProjectPath}</code></div>
        <div><strong>Configuration</strong><code>{session.context.configuration}</code></div>
        <div><strong>Framework</strong><code>{session.context.targetFramework ?? '<all>'}</code></div>
      </div>

      {session.errorMessage && <div className="error-box">{session.errorMessage}</div>}

      {failedProjects.length > 0 && (
        <div className="diagnostics-list">
          <strong>Failed or cancelled</strong>
          {failedProjects.map(project => (
            <div key={project.projectPath}>
              <span>{project.projectName}</span>
              <small>{project.message ?? project.projectPath}</small>
            </div>
          ))}
        </div>
      )}

      {recentErrors.length > 0 && (
        <div className="diagnostics-errors">
          <strong>Recent error lines</strong>
          {recentErrors.map((log, index) => (
            <code key={`${log.timestamp}-${index}`}>{log.message}</code>
          ))}
        </div>
      )}

      <div className="diagnostics-actions">
        <button className="primary" onClick={copyReport}>
          {copyState === 'copied' ? 'Copied' : copyState === 'failed' ? 'Copy failed' : 'Copy diagnostics'}
        </button>
        <a className="button-link" href={`/api/builds/${encodeURIComponent(session.id)}/log`}>
          Download full log
        </a>
      </div>
    </section>
  );
}
