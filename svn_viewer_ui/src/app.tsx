import { signal, useSignal, useSignalEffect } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { Modal, type FormProps } from "antd";
import { useCallback } from "preact/hooks";
import svnparser from "./context/svnparser";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, {
  publishSvnDiffStream,
  SvnDiffProviderContext,
} from "./context/svnDiffProviderContext";
import Layout, { Header, Content } from "./layout";
import SvnLogs from "./logs";
import { CloseOutlined } from "@ant-design/icons";

const svnDiffProviderContext = SvnDiffProvider();

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

  useSignalEffect(() => {
    serverStatus.value = "";
    network.test_server().then((status) => {
      serverStatus.value = status ?? "";
      if (!!status) {
        fetchSettings();
      }
    });
  });

  const svnLogId = useSignal<string>();
  const svnLogStatus = useSignal<SvnStatusItem>();
  const showSvnLogs = useSignal(false);
  const onFetchLogs = useCallback((status?: SvnStatusItem) => {
    network.fetch_logs(status?.source).then((id) => {
      svnLogId.value = id ?? undefined;
      svnLogStatus.value = status;
      showSvnLogs.value = true;
      busy.value = true;
    });
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
      } else if (!!content.completed) {
        busy.value = false;
        switch (content.job) {
          case "FETCH_DIFFS":
          case "FETCH_UNVERSIONED":
          case "FETCH_LOGS":
            publishSvnDiffStream({
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
            publishSvnDiffStream({
              id,
              job: content.job,
              chunks: (content.data as Chunk1[]) ?? [],
            });
            break;
          case "FETCH_UNVERSIONED":
            publishSvnDiffStream({
              id,
              job: content.job,
              unversioned: (content.data as string)
                .split(/\r|\n/g)
                .filter(Boolean),
            });
            break;
          case "FETCH_LOGS": {
            publishSvnDiffStream({
              id,
              job: content.job,
              logs: [
                {
                  logs: svnparser.parse_logs(content.data as string),
                },
              ],
            });
            break;
          }
          default:
            break;
        }
      } else if (!!content.error) {
        console.warn(content.error);
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
          title={serverStatus}
          busy={busy.value}
          source$={settings}
          onFinish={onSettingsChange}
          onFetch={onFetchStatus}
          pickDir={pickDir}
        />
      </Header>
      <SvnDiffProviderContext.Provider value={svnDiffProviderContext}>
        <Content>
          <Diffs
            status={status.value}
            settings={settings}
            fetchLogs={onFetchLogs}
          />
        </Content>
        <Modal
          title="svn logs"
          width="80vw"
          style={{ maxHeight: "80vh" }}
          closable
          closeIcon={
            <CloseOutlined onClick={() => (showSvnLogs.value = false)} />
          }
          open={showSvnLogs.value}
          cancelButtonProps={{ style: { display: "none" } }}
          okButtonProps={{ style: { display: "none" } }}
        >
          <SvnLogs logId={svnLogId} status={svnLogStatus} busy={busy} />
        </Modal>
      </SvnDiffProviderContext.Provider>
    </Layout>
  );
}
