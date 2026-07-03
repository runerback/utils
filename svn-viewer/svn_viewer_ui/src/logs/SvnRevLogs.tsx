import { useSignals } from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import "./logs.css";
import { Skeleton } from "antd";
import { lazy, Suspense } from "preact/compat";

const SvnRevLogsCard = lazy(() => import("./SvnRevLogsCard"));

export default (props: {
  dir: string;
  logs: ReadonlySignal<SvnLog[]>;
  busy: ReadonlySignal<boolean>;
}) => {
  useSignals();
  return (
    <div className="svnlogs">
      {props.busy.value && props.logs.value.length === 0 && (
        <Skeleton loading />
      )}
      {props.logs.value.map((log, idx) => (
        <Suspense fallback={<Skeleton loading />}>
          <SvnRevLogsCard dir={props.dir} key={idx} log={log} />
        </Suspense>
      ))}
    </div>
  );
};
