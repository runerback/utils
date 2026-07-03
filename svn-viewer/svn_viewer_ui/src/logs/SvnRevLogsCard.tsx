import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { Collapse, Skeleton, Spin } from "antd";
import type { Key } from "preact";
import { useCallback, useContext } from "preact/hooks";
import SvnLogTitle from "./SvnLogTitle";
import { SvnRevLogsContext } from "../context/svnRevLogContext";
import { filter } from "rxjs";
import { lazy, Suspense } from "preact/compat";

const SvnRevLogCard = lazy(() => import("./SvnRevLogCard"));

export default (props: { dir: string; log: SvnLog; key: Key }) => {
  useSignals();
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const taskId = useSignal("");
  const items = useSignal(Array<SvnRevStatusItem>());
  const svnRevLogContext = useContext(SvnRevLogsContext);
  useSignalEffect(() => {
    svnRevLogContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_REVISION_LOGS"))
      .subscribe((e) => {
        if (e.id === taskId.value) {
          if (!!e.items) {
            items.value = [...e.items]
              .map((it) => ({
                ...it,
                highlight: it.source.includes(props.dir),
              }))
              .sort((a, b) =>
                a.highlight === b.highlight ? 0 : a.highlight ? -1 : 1
              );
          }
          if (e.finished) {
            busy.value = false;
          }
        }
      });
  });
  useSignalEffect(() => {
    if (fetching.value && !fetched.value) {
      fetching.value = false;
      fetched.value = true;
      busy.value = true;
      svnRevLogContext
        .provide(props.dir, parseInt(props.log.revision as string))
        .then((id) => {
          if (!!id) {
            taskId.value = id;
          } else {
            taskId.value = "";
            busy.value = false;
          }
        });
    }
  });
  const fetch = useCallback((key?: string[]) => {
    if (typeof key === "undefined") {
      fetched.value = false;
      fetching.value = true;
      return;
    }
    if (fetched.value) {
      return;
    }
    fetching.value = true;
  }, []);
  return (
    <div className="svnrevlogscard">
      <Spin spinning={busy.value}>
        <Collapse
          bordered
          key={props.key}
          onChange={fetch}
          items={[
            {
              label: <SvnLogTitle log={props.log} />,
              children: items.value.map((i, idx) => (
                <Suspense fallback={<Skeleton loading />}>
                  <SvnRevLogCard
                    key={idx}
                    dir={props.dir}
                    log={props.log}
                    item={i}
                  />
                </Suspense>
              )),
            },
          ]}
        />
      </Spin>
    </div>
  );
};
