import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useContext } from "preact/hooks";
import { SvnContext } from "../context/svnContext";
import type { ReadonlySignal } from "@preact/signals-react";
import { filter } from "rxjs";
import SvnLogsCard from "./SvnLogsCard";
import "./logs.css";
import { Skeleton } from "antd";

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  busy: ReadonlySignal<boolean>;
  settings: ReadonlySignal<Settings | undefined>;
}) => {
  useSignals();
  const svnLogs = useSignal(Array<SvnLogs>());
  const svnContext = useContext(SvnContext);
  useSignalEffect(() => {
    svnContext.stream$
      .pipe(filter((it) => it.job === "FETCH_LOGS"))
      .subscribe((e) => {
        if (!!e.logs && e.logs.length > 0) {
          svnLogs.value = [
            ...e.logs
              .filter((it) => !!it.logs && it.logs.length > 0)
              .map((it) => {
                if (!it.status) {
                  if (!!props.status.value) {
                    return {
                      status: props.status.value,
                      logs: it.logs ?? [],
                    };
                  }
                } else {
                  return {
                    status: it.status,
                    logs: it.logs ?? [],
                  };
                }
              })
              .filter(Boolean)
              .map((it) => it!),
          ];
        }
      });
  });
  return (
    <div className="svnlogs">
      {props.busy.value && svnLogs.value.length === 0 && <Skeleton loading />}
      {svnLogs.value.map((log, idx) => (
        <SvnLogsCard key={idx} log={log} settings={props.settings} />
      ))}
    </div>
  );
};
