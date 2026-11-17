import { useEffect, useState } from 'react';
import type { BuildSessionSnapshot, ProjectBuildStatusSnapshot } from '../types';

interface BuildStatusListProps {
  session: BuildSessionSnapshot | null;
}

function normalizePath(path: string): string {
  return path.toLowerCase();
}

function formatDuration(start: string | null, end: string | null, now: Date): string | null {
  if (!start) {
    return null;
  }

  const startTime = new Date(start).getTime();
  const endTime = end ? new Date(end).getTime() : now.getTime();
  const totalSeconds = Math.max(0, Math.floor((endTime - startTime) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}h ${minutes}m ${seconds}s`;
  }

  if (minutes > 0) {
    return `${minutes}m ${seconds}s`;
  }

  return `${seconds}s`;
}

function formatProjectDuration(project: ProjectBuildStatusSnapshot, now: Date): string {
  if (project.state === 'Reused') {
    return 'reused';
  }

  if (project.state === 'Queued') {
    return 'queued';
  }

  return formatDuration(project.startedAt, project.completedAt, now) ?? '—';
}

function useNow(enabled: boolean): Date {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    if (!enabled) {
      return;
    }

    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, [enabled]);

  return now;
}

export function BuildStatusList({ session }: BuildStatusListProps) {
  const now = useNow(session?.state === 'Running' || session?.state === 'Pending');

  if (!session) {
    return (
      <section className="panel status-panel empty-panel">
        <h2>Build status</h2>
        <p>No build has been started.</p>
      </section>
    );
  }

  const planOrder = new Map(
    session.plan?.projects.map(project => [normalizePath(project.projectPath), project.order]) ?? []);

  const projects = [...session.projects].sort((left, right) => {
    const leftOrder = planOrder.get(normalizePath(left.projectPath)) ?? Number.MAX_SAFE_INTEGER;
    const rightOrder = planOrder.get(normalizePath(right.projectPath)) ?? Number.MAX_SAFE_INTEGER;

    if (leftOrder !== rightOrder) {
      return leftOrder - rightOrder;
    }

    return left.projectName.localeCompare(right.projectName);
  });

  const elapsed = formatDuration(session.startedAt, session.completedAt, now) ?? 'waiting';
  const buildingCount = projects.filter(project => project.state === 'Building').length;

  return (
    <section className="panel status-panel">
      <div className="panel-header compact">
        <div>
          <h2>Build status</h2>
          <p>
            {session.isBootstrap ? 'Full bootstrap build' : `Build ${session.id}`}
            {' · '}{elapsed}
            {buildingCount > 0 ? ` · ${buildingCount} building` : ''}
          </p>
        </div>
        <span className={`status-badge ${session.state.toLowerCase()}`}>{session.state}</span>
      </div>

      <div className="status-list">
        {projects.map(project => (
          <div className="status-item" key={project.projectPath} title={project.message ?? project.projectPath}>
            <span className={`status-dot ${project.state.toLowerCase()}`} />
            <div className="status-project">
              <strong>{project.projectName}</strong>
              <small>{project.projectPath}</small>
              {project.message && <small>{project.message}</small>}
            </div>
            <span className="status-elapsed">{formatProjectDuration(project, now)}</span>
            <span className={`status-badge ${project.state.toLowerCase()}`}>{project.state}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
