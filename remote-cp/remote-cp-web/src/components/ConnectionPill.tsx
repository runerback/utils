interface ConnectionPillProps {
  isConnected: boolean;
}

export function ConnectionPill({ isConnected }: ConnectionPillProps) {
  return (
    <span class={`connection-pill ${isConnected ? "connection-pill--online" : "connection-pill--offline"}`}>
      {isConnected ? "Connected" : "Reconnecting..."}
    </span>
  );
}
