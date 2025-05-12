import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useContext } from "preact/hooks";
import { SvnDiffProviderContext } from "../context/svnDiffProviderContext";
import type { ReadonlySignal } from "@preact/signals-react";
import { filter } from "rxjs";
import { Skeleton } from "antd";
import SvnLogsCard from "./SvnLogsCard";
import "./logs.css";

export default (props: {
  logId: ReadonlySignal<string | undefined>;
  status: ReadonlySignal<SvnStatusItem | undefined>;
  busy: ReadonlySignal<boolean>;
}) => {
  useSignals();
  const svnLogs = useSignal<SvnLogs[]>();
  const svnDiffProviderContext = useContext(SvnDiffProviderContext);
  useSignalEffect(() => {
    svnDiffProviderContext.stream$
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
      <Skeleton loading={props.busy.value}>
        {svnLogs.value?.map((log, idx) => (
          <SvnLogsCard fkey={idx.toString()} log={log} />
        ))}
      </Skeleton>
    </div>
  );
};
