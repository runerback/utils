import { signal, useSignal, useSignalEffect } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { type FormProps } from "antd";
import { useCallback } from "preact/hooks";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, {
  publishSvnStream,
  SvnProviderContext,
} from "./context/svnProviderContext";
import SvnLogDiffsProvider, {
  publishSvnLogDiffsStream,
  SvnLogDiffsProviderContext,
} from "./context/svnLogDiffsProviderContext";
import Layout, { Header, Content } from "./layout";
import SvnLogsModal from "./logs/SvnLogsModal";

const svnProviderContext = SvnDiffProvider();
const svnLogDiffsProviderContext = SvnLogDiffsProvider();

const messageId = signal("");
const message = signal<MessageContent>();
network.onMessage.subscribe((e) => {
  if (!!e.content) {
    const content = JSON.parse(e.content) as MessageContent;
    if (!!content?.timestamp) {
      messageId.value = e.id;
      message.value = content;
    }
  }
});

export function App() {
  useSignals();
  const serverStatus = useSignal("");
  const settings = useSignal<Settings>();
  const status = useSignal(Array<SvnStatus>());
  const busy = useSignal(false);

  const fetchSettings = useCallback(() => {
    busy.value = true;
    network.get_settings().then((value) => {
      settings.value = !!value ? { ...value } : undefined;
      busy.value = false;
    });
  }, []);

  const fetchingServerStatus = useSignal(false);
  const fetchServerStatus = useCallback((retry: number) => {
    network.test_server().then((status) => {
      if (!status) {
        if (retry > 0) {
          setTimeout(() => fetchServerStatus(retry - 1), 1000);
          return;
        }
      } else {
        serverStatus.value = status;
      }
      fetchingServerStatus.value = false;
    });
  }, []);
  useSignalEffect(() => {
    if (!serverStatus.value) {
      fetchingServerStatus.value = true;
      fetchServerStatus(10);
    } else {
      fetchSettings();
    }
  });

  const svnLogStatus = useSignal<SvnStatusItem>();
  const showSvnDiffLogs = useSignal(false);
  // const showSvnAllLogs = useSignal(false);
  const onFetchLogs = useCallback((status?: SvnStatusItem) => {
    if (status?.source) {
      network.fetch_logs(status.source).then(() => {
        svnLogStatus.value = status;
        showSvnDiffLogs.value = true;
        busy.value = true;
      });
    }
  }, []);

  useSignalEffect(() => {
    const id = messageId.value;
    if (!id) {
      return;
    }
    const content = message.value;
    if (!content) {
      return;
    }
    if (!!content?.timestamp) {
      if (!!content.processing) {
        busy.value = true;
        switch (content.job) {
          case "FETCH_STATUS":
            status.value = [];
            break;
          default:
            break;
        }
      } else if (!!content.completed || !!content.error) {
        if (!!content.error) {
          console.warn(content.error);
        }
        busy.value = false;
        switch (content.job) {
          case "FETCH_DIFFS":
          case "FETCH_UNVERSIONED":
          case "FETCH_LOGS":
            publishSvnStream({
              id,
              job: content.job,
              finished: true,
            });
            break;
          case "FETCH_LOG_DIFFS":
            publishSvnLogDiffsStream({
              id,
              job: content.job,
              finished: true,
            });
            break;
          default:
            break;
        }
      } else if (!!content.data) {
        switch (content.job) {
          case "FETCH_STATUS":
            status.value = (content.data as SvnStatus[]) ?? [];
            break;
          case "FETCH_DIFFS":
            publishSvnStream({
              id,
              job: content.job,
              chunks: (content.data as Chunk1[]) ?? [],
            });
            break;
          case "FETCH_UNVERSIONED":
            publishSvnStream({
              id,
              job: content.job,
              unversioned: (content.data as string)
                .split(/\r|\n/g)
                .filter(Boolean),
            });
            break;
          case "FETCH_LOGS": {
            publishSvnStream({
              id,
              job: content.job,
              logs: [
                {
                  logs: (content.data as SvnLog[]) ?? [],
                },
              ],
            });
            break;
          }
          case "FETCH_LOG_DIFFS": {
            publishSvnLogDiffsStream({
              id,
              job: content.job,
              chunks: (content.data as Chunk1[]) ?? [],
            });
            break;
          }
          default:
            break;
        }
      }
    }
  });

  const pickDir = useCallback(() => {
    network.pick_dir(settings.value?.svn_root).then((dir) => {
      console.log({ pick_dir: dir });
      if (!!dir) {
        settings.value = {
          ...settings.peek(),
          svn_root: dir,
        };
      }
    });
  }, []);

  const onSettingsChange: FormProps<Settings>["onFinish"] = useCallback(
    (values: Settings) => {
      network.update_settings(values).then(() => {
        fetchSettings();
      });
    },
    []
  );

  const onFetchStatus = useCallback(() => {
    network.fetch_status();
  }, []);

  return (
    <Layout>
      <Header>
        <Settings
          loading={fetchingServerStatus}
          title={serverStatus}
          busy={busy.value}
          source$={settings}
          onFinish={onSettingsChange}
          onFetch={onFetchStatus}
          pickDir={pickDir}
        />
      </Header>
      <SvnProviderContext.Provider value={svnProviderContext}>
        <Content>
          <Diffs
            status={status.value}
            settings={settings}
            fetchLogs={onFetchLogs}
          />
        </Content>
        <SvnLogDiffsProviderContext.Provider value={svnLogDiffsProviderContext}>
          <SvnLogsModal
            open={showSvnDiffLogs}
            onClose={() => (showSvnDiffLogs.value = false)}
            status={svnLogStatus}
            settings={settings}
            busy={busy}
          />
        </SvnLogDiffsProviderContext.Provider>
      </SvnProviderContext.Provider>
    </Layout>
  );
}
