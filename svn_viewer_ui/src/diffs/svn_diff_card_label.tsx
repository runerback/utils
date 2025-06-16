import { useComputed } from "@preact/signals-react";
import { Tooltip } from "antd";
import { useRef } from "preact/hooks";

export default (props: { status: SvnStatusItem }) => {
  const headerRef = useRef<HTMLDivElement>(null);
  const stateIcon = useComputed(() => {
    switch (props.status.state) {
      case "M":
        return "Ⓜ️";
      case "D":
        return "❌";
      case "A":
        return "➕";
      case "R":
        return "🔃";
      case "C":
        return "💥";
      case "?":
        return "❓";
      case "!":
        return "❗";
      default:
        return props.status.state;
    }
  });
  const stateTip = useComputed(() => {
    switch (props.status.state) {
      case "M":
        return "Modified";
      case "D":
        return "Deletion";
      case "A":
        return "Adding";
      case "R":
        return "Replaced";
      case "C":
        return "Conflicted";
      case "?":
        return "Not under version control";
      case "!":
        return "Missing";
      default:
        return props.status.state;
    }
  });
  return (
    <div className="changes" ref={headerRef}>
      <div className="state">
        <Tooltip title={stateTip.value}>{stateIcon.value}</Tooltip>
      </div>
      <div className="source">{props.status.source}</div>
    </div>
  );
};
