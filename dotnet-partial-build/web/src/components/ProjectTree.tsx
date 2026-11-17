import { useEffect, useMemo, useState } from 'react';
import type {
  BuildPlan,
  BuildSessionSnapshot,
  GraphProject,
  GraphResponse
} from '../types';

interface ProjectTreeProps {
  graph: GraphResponse | null;
  selectedProjects: Set<string>;
  plan: BuildPlan | null;
  session: BuildSessionSnapshot | null;
  disabled: boolean;
  onToggleProject: (projectPath: string, checked: boolean) => void;
}

interface TreeNodeProps {
  project: GraphProject;
  projectMap: Map<string, GraphProject>;
  selectedProjects: Set<string>;
  plan: BuildPlan | null;
  session: BuildSessionSnapshot | null;
  expanded: Set<string>;
  query: string;
  disabled: boolean;
  ancestry: string[];
  onToggleExpanded: (projectPath: string) => void;
  onToggleProject: (projectPath: string, checked: boolean) => void;
}

function normalizePath(path: string): string {
  return path.toLowerCase();
}

function nodeMatches(project: GraphProject, query: string): boolean {
  const normalizedQuery = query.toLowerCase();
  return project.name.toLowerCase().includes(normalizedQuery) ||
    project.relativePath.toLowerCase().includes(normalizedQuery) ||
    project.fullPath.toLowerCase().includes(normalizedQuery);
}

function nodeOrDescendantMatches(
  project: GraphProject,
  projectMap: Map<string, GraphProject>,
  query: string,
  visited: Set<string>): boolean {
  if (!query) {
    return true;
  }

  if (visited.has(normalizePath(project.fullPath))) {
    return false;
  }

  visited.add(normalizePath(project.fullPath));

  if (nodeMatches(project, query)) {
    return true;
  }

  return project.dependencies.some(dependency => {
    const dependencyProject = projectMap.get(normalizePath(dependency));
    return dependencyProject
      ? nodeOrDescendantMatches(dependencyProject, projectMap, query, visited)
      : false;
  });
}

function TreeNode({
  project,
  projectMap,
  selectedProjects,
  plan,
  session,
  expanded,
  query,
  disabled,
  ancestry,
  onToggleExpanded,
  onToggleProject
}: TreeNodeProps) {
  if (!nodeOrDescendantMatches(project, projectMap, query, new Set())) {
    return null;
  }

  const plannedProject = plan?.projects.find(
    item => normalizePath(item.projectPath) === normalizePath(project.fullPath));
  const sessionProject = session?.projects.find(
    item => normalizePath(item.projectPath) === normalizePath(project.fullPath));
  const isExplicitlySelected = selectedProjects.has(project.fullPath);
  const isAutomaticallyRequired = Boolean(
    plannedProject && !plannedProject.isExplicitlySelected);
  const isChecked = isExplicitlySelected || Boolean(plannedProject);
  const isDisabled = disabled || Boolean(plannedProject?.isRoot) || isAutomaticallyRequired;
  const isExpanded = query ? true : expanded.has(project.fullPath);
  const children = project.dependencies
    .map(path => projectMap.get(normalizePath(path)))
    .filter((child): child is GraphProject => Boolean(child));

  let badge = 'Reuse';
  let badgeClass = 'reuse';

  if (sessionProject) {
    badge = sessionProject.state;
    badgeClass = sessionProject.state.toLowerCase();
  }
  else if (plannedProject?.isRoot) {
    badge = 'Root';
    badgeClass = 'root';
  }
  else if (plannedProject?.isExplicitlySelected) {
    badge = 'Rebuild';
    badgeClass = 'rebuild';
  }
  else if (plannedProject) {
    badge = 'Required';
    badgeClass = 'required';
  }

  return (
    <li className="tree-node">
      <div className="tree-row" title={plannedProject?.reason ?? sessionProject?.message ?? project.fullPath}>
        <button
          className="tree-expander"
          disabled={children.length === 0}
          onClick={() => onToggleExpanded(project.fullPath)}
          aria-label={isExpanded ? 'Collapse project' : 'Expand project'}>
          {children.length === 0 ? '•' : isExpanded ? '▾' : '▸'}
        </button>

        <label className="tree-label">
          <input
            type="checkbox"
            checked={isChecked}
            disabled={isDisabled}
            onChange={event => onToggleProject(project.fullPath, event.target.checked)} />
          <span className="project-name">{project.name}</span>
          <span className="project-path">{project.relativePath}</span>
        </label>

        <span className={`status-badge ${badgeClass}`}>{badge}</span>
      </div>

      {children.length > 0 && isExpanded && (
        <ul>
          {children.map(child => {
            if (ancestry.some(path => normalizePath(path) === normalizePath(child.fullPath))) {
              return null;
            }

            return (
              <TreeNode
                key={`${project.fullPath}|${child.fullPath}`}
                project={child}
                projectMap={projectMap}
                selectedProjects={selectedProjects}
                plan={plan}
                session={session}
                expanded={expanded}
                query={query}
                disabled={disabled}
                ancestry={[...ancestry, project.fullPath]}
                onToggleExpanded={onToggleExpanded}
                onToggleProject={onToggleProject} />
            );
          })}
        </ul>
      )}
    </li>
  );
}

export function ProjectTree({
  graph,
  selectedProjects,
  plan,
  session,
  disabled,
  onToggleProject
}: ProjectTreeProps) {
  const [query, setQuery] = useState('');
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const projectMap = useMemo(() => {
    return new Map(
      graph?.projects.map(project => [normalizePath(project.fullPath), project]) ?? []);
  }, [graph]);

  const rootProject = graph ? projectMap.get(normalizePath(graph.rootProjectPath)) : undefined;

  useEffect(() => {
    if (graph) {
      setExpanded(new Set([graph.rootProjectPath]));
    }
  }, [graph]);

  const toggleExpanded = (projectPath: string) => {
    setExpanded(current => {
      const next = new Set(current);

      if (next.has(projectPath)) {
        next.delete(projectPath);
      }
      else {
        next.add(projectPath);
      }

      return next;
    });
  };

  const expandAll = () => {
    setExpanded(new Set(graph?.projects.map(project => project.fullPath) ?? []));
  };

  if (!graph || !rootProject) {
    return (
      <section className="panel tree-panel empty-panel">
        <h2>Projects</h2>
        <p>Load a root project to display its dependency tree.</p>
      </section>
    );
  }

  return (
    <section className="panel tree-panel">
      <div className="panel-header compact">
        <div>
          <h2>Projects</h2>
          <p>{graph.projects.length} projects · {selectedProjects.size} selected</p>
        </div>
        <div className="button-row">
          <button onClick={expandAll}>Expand</button>
          <button onClick={() => setExpanded(new Set())}>Collapse</button>
        </div>
      </div>

      <input
        className="search-input"
        value={query}
        placeholder="Search project name or path…"
        onChange={event => setQuery(event.target.value)} />

      <div className="tree-scroll">
        <ul className="project-tree">
          <TreeNode
            project={rootProject}
            projectMap={projectMap}
            selectedProjects={selectedProjects}
            plan={plan}
            session={session}
            expanded={expanded}
            query={query}
            disabled={disabled}
            ancestry={[]}
            onToggleExpanded={toggleExpanded}
            onToggleProject={onToggleProject} />
        </ul>
      </div>
    </section>
  );
}
