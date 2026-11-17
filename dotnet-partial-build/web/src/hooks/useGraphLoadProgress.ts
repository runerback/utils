import { useCallback, useState } from 'react';
import { HubConnection, HubConnectionBuilder, LogLevel } from '@microsoft/signalr';
import type { ProjectGraphLoadProgress } from '../types';

export function useGraphLoadProgress() {
  const [progress, setProgress] = useState<ProjectGraphLoadProgress | null>(null);
  const [connected, setConnected] = useState(false);

  const runWithProgress = useCallback(async <T,>(
    operation: (progressId: string) => Promise<T>): Promise<T> => {
    const progressId = crypto.randomUUID();
    let connection: HubConnection | null = null;

    setProgress({
      progressId,
      stage: 'Started',
      projectPath: null,
      projectName: null,
      loadedCount: 0,
      discoveredCount: 1,
      pendingCount: 1,
      message: 'Connecting for graph progress…',
      timestamp: new Date().toISOString()
    });

    try {
      connection = new HubConnectionBuilder()
        .withUrl('/hubs/builds')
        .withAutomaticReconnect()
        .configureLogging(LogLevel.Warning)
        .build();

      connection.on('GraphLoadProgress', (event: ProjectGraphLoadProgress) => {
        setProgress(event);
      });

      await connection.start();
      await connection.invoke('JoinGraphLoad', progressId);
      setConnected(true);
    }
    catch {
      setConnected(false);
      setProgress(current => current && {
        ...current,
        message: 'Graph progress connection unavailable; loading continues…'
      });
    }

    try {
      return await operation(progressId);
    }
    finally {
      if (connection) {
        try {
          await connection.invoke('LeaveGraphLoad', progressId);
        }
        catch {
          // The connection may already be closed.
        }

        await connection.stop();
      }

      setConnected(false);
    }
  }, []);

  return {
    progress,
    connected,
    runWithProgress,
    clearProgress: () => setProgress(null)
  };
}
