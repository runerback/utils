import { useComputed } from "@preact/signals-react";
import SvnStateIcon from "../components/common/SvnStateIcon";

export default (props: { status: SvnStatusItem }) => {
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
    <div className="changes">
      <div className="state">
        <span className="tooltip">{stateTip.value}</span>
        <SvnStateIcon state={props.status.state} />
      </div>
      <div className="source">{props.status.source}</div>
    </div>
  );
};
