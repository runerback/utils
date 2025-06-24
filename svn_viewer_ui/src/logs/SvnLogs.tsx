import { useSignals } from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import SvnLogsCard from "./SvnLogsCard";
import "./logs.css";
import { Skeleton } from "antd";
import { SvnSettingsContext } from "../context/settingsContext";
import { useContext } from "preact/hooks";

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  logs: ReadonlySignal<SvnLogs[]>;
  busy: ReadonlySignal<boolean>;
}) => {
  useSignals();
  const settingsContext = useContext(SvnSettingsContext);
  return (
    <div className="svnlogs">
      {props.busy.value && props.logs.value.length === 0 && (
        <Skeleton loading />
      )}
      {props.logs.value.map((log, idx) => (
        <SvnLogsCard key={idx} log={log} settings={settingsContext.current$} />
      ))}
    </div>
  );
};
