import { useEffect, useMemo, useRef, useState } from 'react';
import type { BuildLogEvent, BuildSessionSnapshot } from '../types';
import { isErrorLog } from '../utils/logFilters';

interface LogPanelProps {
  logs: BuildLogEvent[];
  session: BuildSessionSnapshot | null;
  connectionState: string;
  onClear: () => void;
}

export function LogPanel({ logs, session, connectionState, onClear }: LogPanelProps) {
  const [projectFilter, setProjectFilter] = useState('all');
  const [errorsOnly, setErrorsOnly] = useState(false);
  const [query, setQuery] = useState('');
  const [autoScroll, setAutoScroll] = useState(true);
  const scrollContainer = useRef<HTMLDivElement | null>(null);

  const projectOptions = useMemo(() => {
    const names = new Map<string, string>();

    for (const log of logs) {
      if (log.projectPath && log.projectName) {
        names.set(log.projectPath, log.projectName);
      }
    }

    for (const project of session?.projects ?? []) {
      names.set(project.projectPath, project.projectName);
    }

    return [...names.entries()]
      .map(([path, name]) => ({ path, name }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }, [logs, session]);

  const filteredLogs = logs.filter(log => {
    if (projectFilter !== 'all' && log.projectPath !== projectFilter) {
      return false;
    }

    if (errorsOnly && !isErrorLog(log)) {
      return false;
    }

    if (query && !log.message.toLowerCase().includes(query.toLowerCase())) {
      return false;
    }

    return true;
  });

  useEffect(() => {
    if (session?.state === 'Failed') {
      setErrorsOnly(true);
    }
  }, [session?.state]);

  useEffect(() => {
    if (autoScroll && scrollContainer.current) {
      scrollContainer.current.scrollTop = scrollContainer.current.scrollHeight;
    }
  }, [filteredLogs.length, autoScroll]);

  return (
    <section className="panel log-panel">
      <div className="panel-header compact">
        <div>
          <h2>Realtime logs</h2>
          <p>{filteredLogs.length} of {logs.length} lines · SignalR {connectionState}</p>
        </div>
        <button onClick={onClear} disabled={logs.length === 0}>Clear view</button>
      </div>

      <div className="log-toolbar">
        <select value={projectFilter} onChange={event => setProjectFilter(event.target.value)}>
          <option value="all">All projects</option>
          {projectOptions.map(project => (
            <option value={project.path} key={project.path}>{project.name}</option>
          ))}
        </select>

        <input
          value={query}
          placeholder="Filter log text…"
          onChange={event => setQuery(event.target.value)} />

        <label className="checkbox-line compact">
          <input
            type="checkbox"
            checked={errorsOnly}
            onChange={event => setErrorsOnly(event.target.checked)} />
          <span>Errors only</span>
        </label>

        <label className="checkbox-line compact">
          <input
            type="checkbox"
            checked={autoScroll}
            onChange={event => setAutoScroll(event.target.checked)} />
          <span>Auto-scroll</span>
        </label>
      </div>

      <div className="log-scroll" ref={scrollContainer}>
        {filteredLogs.length === 0 ? (
          <div className="empty-log">No log lines to display.</div>
        ) : filteredLogs.map((log, index) => (
          <div
            className={`log-line ${log.stream === 'StandardError' ? 'error' : ''}`}
            key={`${log.timestamp}-${index}`}>
            <span className="log-time">{new Date(log.timestamp).toLocaleTimeString()}</span>
            <span className="log-project">{log.projectName ?? 'build'}</span>
            <span className="log-message">{log.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
