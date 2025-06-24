import { useSignal } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import Settings from "./settings";
import SvnDiffProvider, { SvnDiffContext } from "./context/svnDiffContext";
import SvnLogDiffsProvider, {
  SvnLogDiffsContext,
} from "./context/svnLogDiffsContext";
import Layout, { Header, Content } from "./layout";
import SvnTreeContextProvider, {
  SvnTreeContext,
} from "./context/svnTreeContext";
import SvnInfoContextProvider, {
  SvnInfoContext,
} from "./context/svnInfoContext";
import { StatusContext } from "./context/statusContext";
import type { NotificationInstance } from "antd/es/notification/interface";
import { lazy, Suspense } from "preact/compat";

const svnContext = SvnDiffProvider();
const svnLogDiffsContext = SvnLogDiffsProvider();
const svnTreeContext = SvnTreeContextProvider();
const svnInfoContext = SvnInfoContextProvider();

const Diffs = lazy(() => import("./diffs/SvnDiffs"));
const SvnLogsModal = lazy(() => import("./logs/SvnLogsModal"));
const SvnTreeModal = lazy(() => import("./tree/SvnTreeModal"));

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  useSignals();
  const statusContext = useContext(StatusContext);

  const svnLogStatus = useSignal<SvnStatusItem>();
  const showSvnDiffLogs = useSignal(false);
  const onFetchLogs = useCallback((status?: SvnStatusItem) => {
    if (status?.source) {
      network.fetch_logs(status.source).then(() => {
        svnLogStatus.value = status;
        showSvnDiffLogs.value = true;
        statusContext.busy();
      });
    }
  }, []);
  const showSvnTree = useSignal(false);

  return (
    <Layout>
      <Header>
        <Settings
          notify={props.notify}
          onFetchTree={() => (showSvnTree.value = true)}
        />
      </Header>
      <SvnDiffContext.Provider value={svnContext}>
        <Content>
          <Suspense fallback={<div>loading</div>}>
            <Diffs fetchLogs={onFetchLogs} />
          </Suspense>
        </Content>
        <SvnLogDiffsContext.Provider value={svnLogDiffsContext}>
          <Suspense fallback={<div>loading</div>}>
            <SvnLogsModal
              open={showSvnDiffLogs}
              onClose={() => (showSvnDiffLogs.value = false)}
              status={svnLogStatus}
            />
          </Suspense>
        </SvnLogDiffsContext.Provider>
        <SvnTreeContext.Provider value={svnTreeContext}>
          <SvnInfoContext.Provider value={svnInfoContext}>
            <Suspense fallback={<div>loading</div>}>
              <SvnTreeModal
                open={showSvnTree}
                onClose={() => (showSvnTree.value = false)}
                fetchLogs={onFetchLogs}
              />
            </Suspense>
          </SvnInfoContext.Provider>
        </SvnTreeContext.Provider>
      </SvnDiffContext.Provider>
    </Layout>
  );
};
