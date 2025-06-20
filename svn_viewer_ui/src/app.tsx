import { useSignal, useSignalEffect } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useSignals } from "@preact/signals-react/runtime";
import { type FormProps } from "antd";
import { useCallback, useContext } from "preact/hooks";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, {
  publishSvnDiffStream,
  SvnDiffContext,
} from "./context/svnDiffContext";
import SvnLogDiffsProvider, {
  publishSvnLogDiffsStream,
  SvnLogDiffsContext,
} from "./context/svnLogDiffsContext";
import Layout, { Header, Content } from "./layout";
import SvnLogsModal from "./logs/SvnLogsModal";
import SvnTreeContextProvider, {
  publishSvnTreeStream,
  SvnTreeContext,
} from "./context/svnTreeContext";
import SvnInfoContextProvider, {
  publishSvnInfoStream,
  SvnInfoContext,
} from "./context/svnInfoContext";
import SvnTreeModal from "./tree/SvnTreeModal";
import {
  onSettingsFetched,
  SvnSettingsContext,
} from "./context/settingsContext";
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
  const status = useSignal(Array<SvnStatus>());
  const busy = useSignal(false);

  const fetchSettingsId = useSignal("");
  const fetchSettings = useCallback(() => {
    busy.value = true;
    network.get_settings().then((value) => {
      if (!!value) {
        onSettingsFetched(value);
      }
      busy.value = false;
    });
  }, []);

  const svnLogStatus = useSignal<SvnStatusItem>();
  const showSvnDiffLogs = useSignal(false);
  const onFetchLogs = useCallback((status?: SvnStatusItem) => {
    if (status?.source) {
      network.fetch_logs(status.source).then(() => {
        svnLogStatus.value = status;
        showSvnDiffLogs.value = true;
        busy.value = true;
      });
    }
  }, []);
  const showSvnTree = useSignal(false);

  const messageId = useSignal("");
  const message = useSignal<MessageContent>();
  useSignalEffect(() => {
    network.messages$.subscribe((e: Message) => {
      if (!!e.content) {
        const content = JSON.parse(e.content) as MessageContent;
        if (!!content?.timestamp) {
          messageId.value = e.id;
          message.value = content;
        }
      }
    });
    network.errors$.subscribe((e: any) => {
      props.notify(`${e}`, "error");
      busy.value = false;
    });
  });

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
        props.notify(`${content.job ?? "Something"} started`, "success");
        busy.value = true;
        switch (content.job) {
          case "FETCH_STATUS":
            status.value = [];
            break;
          default:
            break;
        }
      } else if (!!content.completed || !!content.error) {
        busy.value = false;
        if (!!content.error) {
          console.warn(content.error);
          props.notify(
            `${content.job ?? "Something"} failed: ${
              content.error ?? "Unknown Error"
            }`,
            "warning"
          );
        }
        if (!!content.completed) {
          props.notify(`${content.job ?? "Something"} finished`, "success");
          switch (content.job) {
            case "FETCH_SETTINGS":
              fetchSettings();
              break;
            case "FETCH_DIFFS":
            case "FETCH_UNVERSIONED":
            case "FETCH_FILE_REMOTE":
            case "FETCH_LOGS":
              publishSvnDiffStream({
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
            case "FETCH_TREE":
              publishSvnTreeStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            case "FETCH_INFO":
              publishSvnInfoStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            default:
              break;
          }
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
          case "FETCH_FILE_REMOTE":
            publishSvnDiffStream({
              id,
              job: content.job,
              missing: (content.data as string).split(/\r|\n/g).filter(Boolean),
            });
            break;
          case "FETCH_LOGS": {
            publishSvnDiffStream({
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
          case "FETCH_TREE": {
            publishSvnTreeStream({
              id,
              job: content.job,
              nodes: (
                (content.data as {
                  name: string;
                  dir?: boolean;
                  children?: boolean;
                }[]) ?? []
              ).map((it) => ({
                name: it.name,
                kind: !!it.dir ? "DIR" : "FILE",
                expandable: it.children,
              })),
            });
            break;
          }
          case "FETCH_INFO":
            publishSvnInfoStream({
              id,
              job: content.job,
              info: content.data as SvnTreeNodeInfo,
            });
            break;
          default:
            break;
        }
      }
    }
  });

  const svnSettingsContext = useContext(SvnSettingsContext);
  const onFetchSettings: FormProps<Settings>["onFinish"] = useCallback(
    (values: Settings) => {
      busy.value = true;
      svnSettingsContext.provide(values).then((id) => {
        fetchSettingsId.value = !!id ? id : "";
      });
    },
    []
  );
  const onSettingsRequestValidateFailed = useCallback((error: any) => {
    props.notify(error.toString(), "error");
  }, []);

  const onFetchStatus = useCallback(() => {
    busy.value = true;
    network.fetch_status();
  }, []);

  const fetchInfo = useCallback((path: string) => {
    busy.value = true;
    return svnInfoContext.provide(path);
  }, []);

  return (
    <Layout>
      <Header>
        <Settings
          busy={busy.value}
          onFetchSettings={fetchSettings}
          onRequest={onFetchSettings}
          onValidateFailed={onSettingsRequestValidateFailed}
          onFetchStatus={onFetchStatus}
          onFetchTree={() => (showSvnTree.value = true)}
        />
      </Header>
      <SvnDiffContext.Provider value={svnContext}>
        <Content>
          <Diffs status={status.value} fetchLogs={onFetchLogs} />
        </Content>
        <SvnLogDiffsContext.Provider value={svnLogDiffsContext}>
          <SvnLogsModal
            open={showSvnDiffLogs}
            onClose={() => (showSvnDiffLogs.value = false)}
            status={svnLogStatus}
            busy={busy}
          />
        </SvnLogDiffsContext.Provider>
        <SvnTreeContext.Provider value={svnTreeContext}>
          <SvnInfoContext.Provider value={svnInfoContext}>
            <SvnTreeModal
              open={showSvnTree}
              onClose={() => (showSvnTree.value = false)}
              busy={busy}
              onFetchInfo={fetchInfo}
              onFetched={() => (busy.value = false)}
            />
          </SvnInfoContext.Provider>
        </SvnTreeContext.Provider>
      </SvnDiffContext.Provider>
    </Layout>
  );
};
