import type {
  BuildContextOptions,
  BuildPlanResponse,
  BuildSessionSnapshot,
  FileSystemBrowseResponse,
  GraphResponse
} from '../types';

export class ApiError extends Error {
  readonly validationErrors: string[];

  constructor(message: string, validationErrors: string[] = []) {
    super(message);
    this.name = 'ApiError';
    this.validationErrors = validationErrors;
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    }
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new ApiError(
      payload?.error ?? `Request failed with status ${response.status}.`,
      payload?.validationErrors ?? []);
  }

  return payload as T;
}

export function browseFileSystem(path?: string): Promise<FileSystemBrowseResponse> {
  const query = path ? `?path=${encodeURIComponent(path)}` : '';
  return request<FileSystemBrowseResponse>(`/api/filesystem/browse${query}`);
}

export function loadGraph(
  context: BuildContextOptions,
  progressId?: string): Promise<GraphResponse> {
  return request<GraphResponse>('/api/graph/load', {
    method: 'POST',
    body: JSON.stringify({ context, progressId })
  });
}

export function createPlan(
  context: BuildContextOptions,
  rebuildProjectPaths: string[],
  validateOutputs: boolean): Promise<BuildPlanResponse> {
  return request<BuildPlanResponse>('/api/plan', {
    method: 'POST',
    body: JSON.stringify({
      context,
      rebuildProjectPaths,
      validateOutputs
    })
  });
}

export function startBuild(
  context: BuildContextOptions,
  rebuildProjectPaths: string[]): Promise<BuildSessionSnapshot> {
  return request<BuildSessionSnapshot>('/api/builds', {
    method: 'POST',
    body: JSON.stringify({
      context,
      rebuildProjectPaths,
      validateOutputs: true
    })
  });
}

export function startBootstrap(context: BuildContextOptions): Promise<BuildSessionSnapshot> {
  return request<BuildSessionSnapshot>('/api/builds/bootstrap', {
    method: 'POST',
    body: JSON.stringify({ context })
  });
}

export function getBuild(buildId: string): Promise<BuildSessionSnapshot> {
  return request<BuildSessionSnapshot>(`/api/builds/${encodeURIComponent(buildId)}`);
}

export function cancelBuild(buildId: string): Promise<void> {
  return request<void>(`/api/builds/${encodeURIComponent(buildId)}/cancel`, {
    method: 'POST'
  });
}
