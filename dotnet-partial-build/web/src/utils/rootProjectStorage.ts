const LastRootProjectKey = 'dotnet-partial-build:last-root-project';
const LastRootFolderKey = 'dotnet-partial-build:last-root-folder';

function getDirectoryPath(path: string): string | null {
  const separatorIndex = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
  return separatorIndex > 0 ? path.slice(0, separatorIndex) : null;
}

export function getSavedRootProject(): string | null {
  try {
    return window.localStorage.getItem(LastRootProjectKey);
  }
  catch {
    return null;
  }
}

export function getSavedRootFolder(): string | null {
  try {
    return window.localStorage.getItem(LastRootFolderKey);
  }
  catch {
    return null;
  }
}

export function saveRootProject(projectPath: string): void {
  try {
    window.localStorage.setItem(LastRootProjectKey, projectPath);

    const folder = getDirectoryPath(projectPath);

    if (folder) {
      window.localStorage.setItem(LastRootFolderKey, folder);
    }
  }
  catch {
    // Local storage may be unavailable in private or restricted browser modes.
  }
}
