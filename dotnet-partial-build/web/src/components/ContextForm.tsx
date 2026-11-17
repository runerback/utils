import { useEffect, useState } from 'react';
import type { BuildContextOptions, ProjectGraphLoadProgress } from '../types';
import { getSavedRootFolder, saveRootProject } from '../utils/rootProjectStorage';
import { FolderPicker } from './FolderPicker';

interface ContextFormProps {
  context: BuildContextOptions;
  disabled: boolean;
  loading: boolean;
  graphProgress: ProjectGraphLoadProgress | null;
  onChange: (context: BuildContextOptions) => void;
  onLoadGraph: () => void;
}

function serializeProperties(properties: Record<string, string>): string {
  return Object.entries(properties)
    .map(([key, value]) => `${key}=${value}`)
    .join('\n');
}

function parseProperties(text: string): Record<string, string> {
  const result: Record<string, string> = {};

  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();

    if (!line || line.startsWith('#')) {
      continue;
    }

    const separator = line.indexOf('=');

    if (separator <= 0) {
      continue;
    }

    result[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
  }

  return result;
}

export function ContextForm({
  context,
  disabled,
  loading,
  graphProgress,
  onChange,
  onLoadGraph
}: ContextFormProps) {
  const [propertiesText, setPropertiesText] = useState(
    serializeProperties(context.additionalProperties));
  const [pickerOpen, setPickerOpen] = useState(false);

  useEffect(() => {
    setPropertiesText(serializeProperties(context.additionalProperties));
  }, [context.additionalProperties]);

  const update = (patch: Partial<BuildContextOptions>) => {
    onChange({ ...context, ...patch });
  };

  const commitProperties = () => {
    update({ additionalProperties: parseProperties(propertiesText) });
  };

  return (
    <section className="panel context-panel">
      <div className="panel-header">
        <div>
          <h2>Build context</h2>
          <p>Choose the output project and the exact build context.</p>
        </div>
        <button
          className="primary"
          disabled={disabled || loading || !context.rootProjectPath.trim()}
          onClick={onLoadGraph}>
          {loading ? 'Loading…' : 'Load graph'}
        </button>
      </div>

      <label className="field root-field">
        <span>Root project (.csproj)</span>
        <div className="root-input-row">
          <input
            value={context.rootProjectPath}
            disabled={disabled}
            placeholder="G:\repo\src\Host\Host.csproj"
            onChange={event => update({ rootProjectPath: event.target.value })} />
          <button type="button" disabled={disabled} onClick={() => setPickerOpen(true)}>
            Browse
          </button>
        </div>
      </label>

      {(loading || graphProgress) && (
        <div className="graph-progress" aria-live="polite">
          <div className="graph-progress-summary">
            <strong>{graphProgress?.message ?? 'Preparing project graph…'}</strong>
            <span>
              {graphProgress
                ? `${graphProgress.loadedCount}/${graphProgress.discoveredCount} loaded · ${graphProgress.pendingCount} pending`
                : 'Connecting…'}
            </span>
          </div>
          <div className="graph-progress-track">
            <div
              className={`graph-progress-value ${graphProgress ? '' : 'indeterminate'}`}
              style={{
                width: graphProgress
                  ? `${Math.min(100, Math.round((graphProgress.loadedCount / Math.max(graphProgress.discoveredCount, 1)) * 100))}%`
                  : '35%'
              }} />
          </div>
        </div>
      )}

      <div className="field-grid">
        <label className="field">
          <span>Configuration</span>
          <select
            value={context.configuration}
            disabled={disabled}
            onChange={event => update({ configuration: event.target.value })}>
            <option>Debug</option>
            <option>Release</option>
          </select>
        </label>

        <label className="field">
          <span>Target framework</span>
          <input
            value={context.targetFramework ?? ''}
            disabled={disabled}
            placeholder="net8.0"
            onChange={event => update({ targetFramework: event.target.value || null })} />
        </label>

        <label className="field">
          <span>Runtime identifier</span>
          <input
            value={context.runtimeIdentifier ?? ''}
            disabled={disabled}
            placeholder="win-x64"
            onChange={event => update({ runtimeIdentifier: event.target.value || null })} />
        </label>

        <label className="field">
          <span>Platform</span>
          <input
            value={context.platform ?? ''}
            disabled={disabled}
            placeholder="AnyCPU"
            onChange={event => update({ platform: event.target.value || null })} />
        </label>
      </div>

      <label className="checkbox-line">
        <input
          type="checkbox"
          checked={context.restoreBeforeBuild}
          disabled={disabled}
          onChange={event => update({ restoreBeforeBuild: event.target.checked })} />
        <span>Restore root dependency graph before partial builds (bootstrap always restores)</span>
      </label>

      <label className="field">
        <span>Additional MSBuild properties</span>
        <textarea
          rows={3}
          value={propertiesText}
          disabled={disabled}
          placeholder={'WarningsAsErrors=\nCustomProperty=value'}
          onChange={event => setPropertiesText(event.target.value)}
          onBlur={commitProperties} />
      </label>

      <p className="storage-note">
        Build settings are remembered separately for each root project.
      </p>
      {pickerOpen && (
        <FolderPicker
          initialPath={context.rootProjectPath || getSavedRootFolder() || ''}
          onSelect={projectPath => {
            saveRootProject(projectPath);
            update({ rootProjectPath: projectPath });
            setPickerOpen(false);
          }}
          onClose={() => setPickerOpen(false)} />
      )}
    </section>
  );
}
