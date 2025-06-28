import { useSignal } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { useCallback, useContext, useMemo } from "preact/hooks";
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
import SvnRevLogsContextProvider, {
  SvnRevLogsContext,
} from "./context/svnRevLogContext";
import { StatusContext } from "./context/statusContext";
import { lazy, Suspense } from "preact/compat";
import { Skeleton } from "antd";
import SvnStatusContextProvider, {
  SvnStatusContext,
} from "./context/svnStatusContext";
import Diffs from "./diffs/SvnDiffs";

const SvnDiffLogsModal = lazy(() => import("./logs/SvnDiffLogsModal"));
const SvnTreeModal = lazy(() => import("./tree/SvnTreeModal"));
const SvnRevLogsModal = lazy(() => import("./logs/SvnRevLogsModal"));

export default () => {
  useSignals();
  const svnContext = useMemo(() => SvnDiffProvider(), []);
  const svnLogDiffsContext = useMemo(() => SvnLogDiffsProvider(), []);
  const svnTreeContext = useMemo(() => SvnTreeContextProvider(), []);
  const svnInfoContext = useMemo(() => SvnInfoContextProvider(), []);
  const svnRevLogsContext = useMemo(() => SvnRevLogsContextProvider(), []);
  const svnStatusContext = useMemo(() => SvnStatusContextProvider(), []);

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

  const svnRevLogDir = useSignal("");
  const showSvnRevLogs = useSignal(false);
  const onFetchRevLogs = useCallback((dir: string) => {
    network.fetch_logs(dir).then(() => {
      svnRevLogDir.value = dir;
      showSvnRevLogs.value = true;
      statusContext.busy();
    });
  }, []);

  const showSvnTree = useSignal(false);

  return (
    <Layout>
      <Header>
        <Settings onFetchTree={() => (showSvnTree.value = true)} />
      </Header>
      <SvnDiffContext.Provider value={svnContext}>
        <Content>
          <SvnStatusContext.Provider value={svnStatusContext}>
            <Diffs fetchLogs={onFetchLogs} />
          </SvnStatusContext.Provider>
        </Content>
        <SvnLogDiffsContext.Provider value={svnLogDiffsContext}>
          <Suspense fallback={<Skeleton loading />}>
            <SvnDiffLogsModal
              open={showSvnDiffLogs}
              onClose={() => (showSvnDiffLogs.value = false)}
              status={svnLogStatus}
            />
          </Suspense>
        </SvnLogDiffsContext.Provider>
        <SvnTreeContext.Provider value={svnTreeContext}>
          <SvnInfoContext.Provider value={svnInfoContext}>
            <Suspense fallback={<Skeleton loading />}>
              <SvnTreeModal
                open={showSvnTree}
                onClose={() => (showSvnTree.value = false)}
                fetchLogs={onFetchLogs}
                fetchRevLogs={onFetchRevLogs}
              />
            </Suspense>
          </SvnInfoContext.Provider>
        </SvnTreeContext.Provider>
        <SvnRevLogsContext.Provider value={svnRevLogsContext}>
          <Suspense fallback={<Skeleton loading />}>
            <SvnRevLogsModal
              dir={svnRevLogDir.value}
              open={showSvnRevLogs}
              onClose={() => (showSvnRevLogs.value = false)}
            />
          </Suspense>
        </SvnRevLogsContext.Provider>
      </SvnDiffContext.Provider>
    </Layout>
  );
};
