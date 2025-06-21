import { useSignal } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, { SvnDiffContext } from "./context/svnDiffContext";
import SvnLogDiffsProvider, {
  SvnLogDiffsContext,
} from "./context/svnLogDiffsContext";
import Layout, { Header, Content } from "./layout";
import SvnLogsModal from "./logs/SvnLogsModal";
import SvnTreeContextProvider, {
  SvnTreeContext,
} from "./context/svnTreeContext";
import SvnInfoContextProvider, {
  SvnInfoContext,
} from "./context/svnInfoContext";
import SvnTreeModal from "./tree/SvnTreeModal";
import { StatusContext } from "./context/statusContext";
import type { NotificationInstance } from "antd/es/notification/interface";

const svnContext = SvnDiffProvider();
const svnLogDiffsContext = SvnLogDiffsProvider();
const svnTreeContext = SvnTreeContextProvider();
const svnInfoContext = SvnInfoContextProvider();

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
          <Diffs fetchLogs={onFetchLogs} />
        </Content>
        <SvnLogDiffsContext.Provider value={svnLogDiffsContext}>
          <SvnLogsModal
            open={showSvnDiffLogs}
            onClose={() => (showSvnDiffLogs.value = false)}
            status={svnLogStatus}
          />
        </SvnLogDiffsContext.Provider>
        <SvnTreeContext.Provider value={svnTreeContext}>
          <SvnInfoContext.Provider value={svnInfoContext}>
            <SvnTreeModal
              open={showSvnTree}
              onClose={() => (showSvnTree.value = false)}
            />
          </SvnInfoContext.Provider>
        </SvnTreeContext.Provider>
      </SvnDiffContext.Provider>
    </Layout>
  );
};
