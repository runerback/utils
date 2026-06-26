/// <reference types="vite/client" />

declare module "pulltorefreshjs" {
  interface PullToRefreshOptions {
    mainElement?: string;
    onRefresh(): void;
  }

  function init(options: PullToRefreshOptions): void;

  export default { init };
}

interface Window {
  showSaveFilePicker?(options?: {
    suggestedName?: string;
    types?: Array<{
      description?: string;
      accept: Record<string, string[]>;
    }>;
  }): Promise<FileSystemFileHandle>;
}
