import { useEffect, useRef, useState } from 'react';
import { HubConnectionBuilder, LogLevel } from '@microsoft/signalr';
import { getBuild } from '../api/client';
import type {
  BuildLogEvent,
  BuildSessionSnapshot,
  ProjectBuildStatusChangedEvent
} from '../types';

interface RealtimeCallbacks {
  onSnapshot: (snapshot: BuildSessionSnapshot) => void;
  onLog: (log: BuildLogEvent) => void;
  onProjectStatus: (status: ProjectBuildStatusChangedEvent) => void;
}

export function useBuildRealtime(buildId: string | null, callbacks: RealtimeCallbacks) {
  const [connectionState, setConnectionState] = useState('Disconnected');
  const callbacksRef = useRef(callbacks);

  useEffect(() => {
    callbacksRef.current = callbacks;
  }, [callbacks]);

  useEffect(() => {
    if (!buildId) {
      setConnectionState('Disconnected');
      return;
    }

    let disposed = false;

    const connection = new HubConnectionBuilder()
      .withUrl('/hubs/builds')
      .withAutomaticReconnect()
      .configureLogging(LogLevel.Warning)
      .build();

    const joinBuild = async () => {
      await connection.invoke('JoinBuild', buildId);
      const snapshot = await getBuild(buildId);

      if (!disposed) {
        callbacksRef.current.onSnapshot(snapshot);
      }
    };

    connection.on('ProjectLog', (log: BuildLogEvent) => {
      callbacksRef.current.onLog(log);
    });

    connection.on('ProjectStatusChanged', (status: ProjectBuildStatusChangedEvent) => {
      callbacksRef.current.onProjectStatus(status);
    });

    connection.on('BuildStarted', (snapshot: BuildSessionSnapshot) => {
      callbacksRef.current.onSnapshot(snapshot);
    });

    connection.on('BuildCompleted', (snapshot: BuildSessionSnapshot) => {
      callbacksRef.current.onSnapshot(snapshot);
    });

    connection.on('BuildFailed', (snapshot: BuildSessionSnapshot) => {
      callbacksRef.current.onSnapshot(snapshot);
    });

    connection.on('BuildCancelled', (snapshot: BuildSessionSnapshot) => {
      callbacksRef.current.onSnapshot(snapshot);
    });

    connection.onreconnecting(() => {
      if (!disposed) {
        setConnectionState('Reconnecting');
      }
    });

    connection.onreconnected(() => {
      if (!disposed) {
        setConnectionState('Connected');
        void joinBuild();
      }
    });

    connection.onclose(() => {
      if (!disposed) {
        setConnectionState('Disconnected');
      }
    });

    setConnectionState('Connecting');

    connection.start()
      .then(async () => {
        if (disposed) {
          return;
        }

        setConnectionState('Connected');
        await joinBuild();
      })
      .catch(() => {
        if (!disposed) {
          setConnectionState('Failed');
        }
      });

    return () => {
      disposed = true;
      void connection.stop();
    };
  }, [buildId]);

  return connectionState;
}
