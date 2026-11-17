import { FormEvent, useEffect, useMemo, useState } from 'react';
import { browseFileSystem } from '../api/client';
import type { FileSystemBrowseResponse } from '../types';

interface FolderPickerProps {
  initialPath: string;
  onSelect: (projectPath: string) => void;
  onClose: () => void;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function FolderPicker({ initialPath, onSelect, onClose }: FolderPickerProps) {
  const [browse, setBrowse] = useState<FileSystemBrowseResponse | null>(null);
  const [pathText, setPathText] = useState(initialPath);
  const [filter, setFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (path?: string | null) => {
    setLoading(true);
    setError(null);

    try {
      const response = await browseFileSystem(path ?? undefined);
      setBrowse(response);
      setPathText(response.currentPath ?? '');
      setFilter('');
    }
    catch (browseError) {
      setError(errorMessage(browseError));
    }
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(initialPath || null);
  }, [initialPath]);

  const filteredDirectories = useMemo(() => {
    const normalizedFilter = filter.toLowerCase();
    return (browse?.directories ?? []).filter(entry =>
      entry.name.toLowerCase().includes(normalizedFilter));
  }, [browse, filter]);

  const filteredProjects = useMemo(() => {
    const normalizedFilter = filter.toLowerCase();
    return (browse?.projects ?? []).filter(entry =>
      entry.name.toLowerCase().includes(normalizedFilter));
  }, [browse, filter]);

  const submitPath = (event: FormEvent) => {
    event.preventDefault();
    void load(pathText);
  };

  return (
    <div className="modal-backdrop">
      <div className="folder-picker" role="dialog" aria-modal="true" aria-label="Choose root project">
        <div className="panel-header compact">
          <div>
            <h2>Choose root project</h2>
            <p>Navigate local folders and select the output .csproj.</p>
          </div>
          <button onClick={onClose} aria-label="Close picker">✕</button>
        </div>

        <form className="picker-path-row" onSubmit={submitPath}>
          <input
            value={pathText}
            placeholder="Folder or .csproj path"
            onChange={event => setPathText(event.target.value)} />
          <button type="submit" disabled={loading}>Go</button>
          <button type="button" disabled={loading} onClick={() => void load(browse?.currentPath ?? pathText)}>
            Refresh
          </button>
          <button type="button" disabled={loading || !browse?.parentPath} onClick={() => void load(browse?.parentPath)}>
            Up
          </button>
        </form>

        <input
          className="picker-filter"
          value={filter}
          placeholder="Filter current folder…"
          onChange={event => setFilter(event.target.value)} />

        {error && <div className="error-box">{error}</div>}

        <div className="picker-content">
          <section>
            <h3>Projects</h3>
            {filteredProjects.length === 0 ? (
              <p className="picker-empty">No .csproj files in this folder.</p>
            ) : (
              <div className="picker-list">
                {filteredProjects.map(project => (
                  <button
                    className="picker-item project-item"
                    key={project.fullPath}
                    title={project.fullPath}
                    onClick={() => onSelect(project.fullPath)}>
                    <span>📦</span>
                    <strong>{project.name}</strong>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section>
            <h3>Folders</h3>
            {filteredDirectories.length === 0 ? (
              <p className="picker-empty">No accessible folders.</p>
            ) : (
              <div className="picker-list">
                {filteredDirectories.map(directory => (
                  <button
                    className="picker-item"
                    key={directory.fullPath}
                    title={directory.fullPath}
                    onClick={() => void load(directory.fullPath)}>
                    <span>📁</span>
                    <strong>{directory.name}</strong>
                  </button>
                ))}
              </div>
            )}
          </section>
        </div>

        <div className="picker-footer">
          <span>{loading ? 'Loading…' : browse?.currentPath ?? 'Drives'}</span>
        </div>
      </div>
    </div>
  );
}
