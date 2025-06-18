import { useSignals } from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import SvnLogsCard from "./SvnLogsCard";
import "./logs.css";
import { Skeleton } from "antd";

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  logs: ReadonlySignal<SvnLogs[]>;
  busy: ReadonlySignal<boolean>;
  settings: ReadonlySignal<Settings | undefined>;
}) => {
  useSignals();
  return (
    <div className="svnlogs">
      {props.busy.value && props.logs.value.length === 0 && (
        <Skeleton loading />
      )}
      {props.logs.value.map((log, idx) => (
        <SvnLogsCard key={idx} log={log} settings={props.settings} />
      ))}
    </div>
  );
};
