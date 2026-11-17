import type { BuildContextOptions } from '../types';

const ProjectSettingsPrefix = 'dotnet-partial-build:project-settings:';
const SettingsVersion = 1;

interface StoredProjectSettings {
  version: number;
  settings: Omit<BuildContextOptions, 'rootProjectPath'>;
}

function normalizeProjectPath(path: string): string {
  return path.trim().toLowerCase();
}

function isProjectPath(path: string): boolean {
  return path.trim().toLowerCase().endsWith('.csproj');
}

function getStorageKey(rootProjectPath: string): string {
  return `${ProjectSettingsPrefix}${normalizeProjectPath(rootProjectPath)}`;
}

function normalizeSettings(rootProjectPath: string, value: unknown): BuildContextOptions | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const settings = value as Partial<BuildContextOptions>;
  const additionalProperties = settings.additionalProperties &&
    typeof settings.additionalProperties === 'object' &&
    !Array.isArray(settings.additionalProperties)
    ? settings.additionalProperties
    : {};

  return {
    rootProjectPath,
    configuration: settings.configuration || 'Debug',
    targetFramework: settings.targetFramework ?? null,
    runtimeIdentifier: settings.runtimeIdentifier ?? null,
    platform: settings.platform ?? null,
    restoreBeforeBuild: settings.restoreBeforeBuild ?? true,
    additionalProperties
  };
}

export function getProjectSettings(rootProjectPath: string): BuildContextOptions | null {
  if (!isProjectPath(rootProjectPath)) {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(getStorageKey(rootProjectPath));

    if (!raw) {
      return null;
    }

    const stored = JSON.parse(raw) as StoredProjectSettings;

    if (stored.version !== SettingsVersion) {
      return null;
    }

    return normalizeSettings(rootProjectPath, stored.settings);
  }
  catch {
    return null;
  }
}

export function saveProjectSettings(context: BuildContextOptions): void {
  if (!isProjectPath(context.rootProjectPath)) {
    return;
  }

  const settings: StoredProjectSettings = {
    version: SettingsVersion,
    settings: {
      configuration: context.configuration,
      targetFramework: context.targetFramework,
      runtimeIdentifier: context.runtimeIdentifier,
      platform: context.platform,
      restoreBeforeBuild: context.restoreBeforeBuild,
      additionalProperties: context.additionalProperties
    }
  };

  try {
    window.localStorage.setItem(
      getStorageKey(context.rootProjectPath),
      JSON.stringify(settings));
  }
  catch {
    // Local storage may be unavailable in private or restricted browser modes.
  }
}
