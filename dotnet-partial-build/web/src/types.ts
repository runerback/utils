export interface BuildContextOptions {
  rootProjectPath: string;
  configuration: string;
  targetFramework: string | null;
  runtimeIdentifier: string | null;
  platform: string | null;
  restoreBeforeBuild: boolean;
  additionalProperties: Record<string, string>;
}

export interface GraphProject {
  fullPath: string;
  name: string;
  relativePath: string;
  dependencies: string[];
  dependents: string[];
}

export interface GraphResponse {
  rootProjectPath: string;
  projects: GraphProject[];
}

export type ProjectGraphLoadStage =
  | 'Started'
  | 'Loading'
  | 'Loaded'
  | 'Completed'
  | 'Failed';

export interface ProjectGraphLoadProgress {
  progressId: string;
  stage: ProjectGraphLoadStage;
  projectPath: string | null;
  projectName: string | null;
  loadedCount: number;
  discoveredCount: number;
  pendingCount: number;
  message: string;
  timestamp: string;
}

export interface PlannedProject {
  projectPath: string;
  projectName: string;
  relativePath: string;
  targetFramework: string | null;
  order: number;
  reason: string;
  isExplicitlySelected: boolean;
  isRoot: boolean;
}

export interface ReusedProject {
  projectPath: string;
  projectName: string;
  relativePath: string;
  targetFramework: string | null;
  expectedOutputPath: string | null;
  validationError: string | null;
}

export interface BuildPlan {
  context: BuildContextOptions;
  rootProjectPath: string;
  projects: PlannedProject[];
  reusedProjects: ReusedProject[];
  hasValidationErrors: boolean;
}

export interface BuildPlanResponse {
  plan: BuildPlan;
  canStart: boolean;
  validationErrors: string[];
}

export type BuildExecutionState =
  | 'Pending'
  | 'Running'
  | 'Succeeded'
  | 'Failed'
  | 'Cancelled';

export type ProjectExecutionState =
  | 'Queued'
  | 'Reused'
  | 'Building'
  | 'Succeeded'
  | 'Failed'
  | 'Skipped'
  | 'Cancelled';

export interface ProjectBuildStatusSnapshot {
  projectPath: string;
  projectName: string;
  state: ProjectExecutionState;
  message: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export type BuildLogStream = 'StandardOutput' | 'StandardError';

export interface BuildLogEvent {
  buildId: string;
  projectPath: string | null;
  projectName: string | null;
  stream: BuildLogStream;
  message: string;
  timestamp: string;
}

export interface BuildSessionSnapshot {
  id: string;
  isBootstrap: boolean;
  state: BuildExecutionState;
  context: BuildContextOptions;
  plan: BuildPlan | null;
  projects: ProjectBuildStatusSnapshot[];
  recentLogs: BuildLogEvent[];
  logFilePath: string | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface ProjectBuildStatusChangedEvent {
  buildId: string;
  projectPath: string;
  projectName: string;
  state: ProjectExecutionState;
  message: string | null;
  timestamp: string;
}

export interface FileSystemEntry {
  name: string;
  fullPath: string;
}

export interface FileSystemBrowseResponse {
  currentPath: string | null;
  parentPath: string | null;
  directories: FileSystemEntry[];
  projects: FileSystemEntry[];
}
