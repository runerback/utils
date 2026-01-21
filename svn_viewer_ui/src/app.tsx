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
import SvnLogsContextProvider, {
  SvnLogsContext,
} from "./context/svnLogsContext";
import SvnCommitContextProvider, {
  SvnCommitContext,
} from "./context/svnCommitContext";
import SvnRevertContextProvider, {
  SvnRevertContext,
} from "./context/svnRevertContext";
import { StatusContext } from "./context/statusContext";
import { lazy, Suspense } from "preact/compat";
import { Skeleton } from "antd";
import SvnStatusContextProvider, {
  SvnStatusContext,
} from "./context/svnStatusContext";
import Diffs from "./diffs/SvnDiffs";
import SvnChangesCommitModal from "./commit/SvnChangesCommitModal";
import { NotifyContext } from "./context/notifyContext";

const SvnDiffLogsModal = lazy(() => import("./logs/SvnDiffLogsModal"));
const SvnTreeModal = lazy(() => import("./tree/SvnTreeModal"));
const SvnRevLogsModal = lazy(() => import("./logs/SvnRevLogsModal"));

export default () => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const notifyContext = useContext(NotifyContext);
  const svnContext = useMemo(() => SvnDiffProvider(), []);
  const svnLogsContext = useMemo(() => SvnLogsContextProvider(), []);
  const svnLogDiffsContext = useMemo(() => SvnLogDiffsProvider(), []);
  const svnTreeContext = useMemo(() => SvnTreeContextProvider(), []);
  const svnInfoContext = useMemo(() => SvnInfoContextProvider(), []);
  const svnRevLogsContext = useMemo(() => SvnRevLogsContextProvider(), []);
  const svnStatusContext = useMemo(() => SvnStatusContextProvider(), []);
  const svnCommitContext = useMemo(() => SvnCommitContextProvider(), []);
  const svnRevertContext = useMemo(
    () =>
      SvnRevertContextProvider({
        notifier: notifyContext,
        status: statusContext,
      }),
    [notifyContext, statusContext],
  );

  const svnRevLogDir = useSignal("");
  const showSvnRevLogs = useSignal(false);
  const onFetchRevLogs = useCallback((dir: string) => {
    network.fetch_logs(dir).then(() => {
      svnRevLogDir.value = dir;
      showSvnRevLogs.value = true;
      statusContext.busy();
    });
  }, []);

  const onCommitChanges = useCallback(
    (params: { files: string[]; message: string; commit?: boolean }) => {
      svnCommitContext
        .commit({
          message: params.message,
          files: params.files,
          commit: params.commit,
        })
        .then((res) => {
          console.log(res);
        });
    },
    [],
  );

  return (
    <SvnCommitContext.Provider value={svnCommitContext}>
      <Layout>
        <Header>
          <Settings
            onFetchTree={svnTreeContext.show}
            onCommitting={svnCommitContext.show}
          />
        </Header>
        <SvnDiffContext.Provider value={svnContext}>
          <Content>
            <SvnStatusContext.Provider value={svnStatusContext}>
              <SvnRevertContext.Provider value={svnRevertContext}>
                <Diffs />
                <Suspense fallback={<Skeleton loading />}>
                  <SvnChangesCommitModal onCommitChanges={onCommitChanges} />
                </Suspense>
              </SvnRevertContext.Provider>
            </SvnStatusContext.Provider>
          </Content>
          <SvnLogsContext.Provider value={svnLogsContext}>
            <SvnLogDiffsContext.Provider value={svnLogDiffsContext}>
              <Suspense fallback={<Skeleton loading />}>
                <SvnDiffLogsModal />
              </Suspense>
            </SvnLogDiffsContext.Provider>
          </SvnLogsContext.Provider>
          <SvnTreeContext.Provider value={svnTreeContext}>
            <SvnInfoContext.Provider value={svnInfoContext}>
              <Suspense fallback={<Skeleton loading />}>
                <SvnTreeModal fetchRevLogs={onFetchRevLogs} />
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
    </SvnCommitContext.Provider>
  );
};
