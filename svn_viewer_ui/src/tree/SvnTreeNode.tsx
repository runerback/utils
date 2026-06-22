import { Button, Space } from "antd";
import SvnTreeNodeIcon from "./SvnTreeNodeIcon";
import type { TreeDataNode } from "./SvnTreeModal";
import { batch, type ReadonlySignal } from "@preact/signals-react";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext, useRef } from "preact/hooks";
import { SvnInfoContext } from "../context/svnInfoContext";
import { filter } from "rxjs";
import SvnLogTitle from "../logs/SvnLogTitle";
import { StatusContext } from "../context/statusContext";
import { lazy, Suspense } from "preact/compat";
import { KeyboardContext } from "../context/keyboardContext";
import SvnStateIcon from "../components/common/SvnStateIcon";
import { NotifyContext } from "../context/notifyContext";

const Info = lazy(() => import("../assets/Sync.svg?react"));
const History = lazy(() => import("../assets/History.svg?react"));

export default (props: {
  node: TreeDataNode;
  openned: boolean;
  busy: ReadonlySignal<boolean>;
  fetchLogs: (status: SvnStatusItem) => void;
  fetchRevLogs: (dir: string) => void;
}) => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const svnInfoContext = useContext(SvnInfoContext);
  const notifyContext = useContext(NotifyContext);
  const keyboardContext = useContext(KeyboardContext);

  const nodeRef = useRef<HTMLDivElement>(null);
  const isVisible = useSignal(false);
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const fetchId = useSignal("");
  const info = useSignal<SvnTreeNodeInfo>();

  const log = useComputed<(SvnLog & { status?: string }) | undefined>(() => {
    const currentInfo = info.value;
    if (!currentInfo || !currentInfo.lastChangedRev) {
      return undefined;
    }
    return {
      revision: currentInfo.lastChangedRev,
      author: currentInfo.lastChangedAuthor,
      timestamp: currentInfo.lastChangedTime,
      status: currentInfo.status,
    };
  });

  const canFetchLogs = useComputed(() => {
    if (!!log.value?.revision && parseInt(log.value.revision) > 0) {
      if (props.node.data.kind === "FILE" && !!log.value.status) {
        return true;
      } else if (props.node.data.kind === "DIR") {
        return true;
      }
    }
    return false;
  });

  useSignalEffect(() => {
    svnInfoContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_INFO"))
      .subscribe((e) => {
        if (e.id === fetchId.value) {
          if (!!e.info) {
            info.value = { ...e.info };
          }
          if (!!e.finished) {
            batch(() => {
              fetched.value = true;
              fetching.value = false;
            });
            svnInfoContext.ready();
          }
        }
      });
  });

  const fetchSubTree = useCallback(() => {
    if (!fetching.value) {
      fetching.value = true;
      svnInfoContext
        .provide({
          root: props.node.key as string,
          status: props.node.data.kind === "FILE",
          flush: keyboardContext.ctrl$.value,
        })
        .then((id) => {
          if (!!id) {
            statusContext.busy();
            fetchId.value = id;
          } else {
            notifyContext.notify("Nothing Happened", "warning");
          }
        });
    }
  }, [props.node.key, props.node.data.kind, keyboardContext.ctrl$.value]);

  const fetchLogs = useCallback(() => {
    const source = props.node.key as string;
    if (!source) {
      console.warn("source is required");
      return;
    }
    if (props.node.data.kind === "FILE") {
      props.fetchLogs({
        state: log.value?.status!,
        source: props.node.key as string,
      });
    } else if (props.node.data.kind === "DIR") {
      props.fetchRevLogs(props.node.key as string);
    }
  }, [props.node, log.value, props.fetchLogs, props.fetchRevLogs]);

  useSignalEffect(() => {
    if (
      isVisible.value &&
      !fetched.value &&
      !fetching.value &&
      !svnInfoContext.reachMaxFetchInfoTaskCount.value
    ) {
      fetchSubTree();
    }
  });

  useSignalEffect(() => {
    const element = nodeRef.current;
    if (!element) {
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          isVisible.value = true;
        }
      },
      { threshold: 0.1 }
    );
    observer.observe(element);
    return () => observer.disconnect();
  });

  return (
    <div className="title" ref={nodeRef}>
      <div className="name">
        <SvnTreeNodeIcon node={props.node.data} opened={props.openned} />
        {props.node.data.name}
      </div>
      <div className="operations">
        {!!log.value && (
          <div className="info">
            <Space>
              {!!log.value.status && <SvnStateIcon state={log.value.status} />}
              <SvnLogTitle log={log.value} />
            </Space>
          </div>
        )}
        <Space>
          <Button
            loading={fetching.value}
            icon={
              <Suspense fallback={<img className="icon" />}>
                <Info className="icon" />
              </Suspense>
            }
            title="Info"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              fetchSubTree();
            }}
          />
          <Button
            loading={canFetchLogs.value && fetching.value}
            disabled={!canFetchLogs.value}
            icon={
              <Suspense fallback={<img className="icon" />}>
                <History className="icon" />
              </Suspense>
            }
            title="Show Logs"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              fetchLogs();
            }}
          />
        </Space>
      </div>
    </div>
  );
};
