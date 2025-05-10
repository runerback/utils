import { signal, useSignal, useSignalEffect } from "@preact/signals-react";
import "./app.css";
import network from "./context/network";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import { type FormProps } from "antd";
import { useCallback } from "preact/hooks";
import svnparser from "./context/svnparser";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, {
  publishSvnDiffStream,
  SvnDiffProviderContext,
} from "./context/svnDiffProviderContext";
import Layout, { Header, Content } from "./layout";

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
  const serverStatus = useSignal("!");
  const settings = useSignal<Settings>();
  const rawStatus = useSignal("");
  const status = useComputed(() => {
    if (!!rawStatus.value) {
      const currentSettings = settings.peek();
      if (!!currentSettings && currentSettings.svn_root) {
        return svnparser.parse_status(rawStatus.value, currentSettings);
      }
    }
    return [];
  });
  const busy = useSignal(false);

  const fetchSettings = useCallback(() => {
    busy.value = true;
    network.get_settings().then((value) => {
      settings.value = !!value ? { ...value } : undefined;
      busy.value = false;
    });
  }, []);

  useSignalEffect(() => {
    network.test_server().then((status) => {
      if (!!status) {
        serverStatus.value = status;
      } else {
        serverStatus.value = ", but 😵";
        return;
      }
      fetchSettings();
    });
  });

  const parseDiff = useCallback((rawDiff: string) => {
    if (!!rawDiff) {
      const currentSettings = settings.peek();
      if (!!currentSettings && currentSettings.svn_root) {
        return svnparser.parse_diff(rawDiff, {
          svn_root: currentSettings.svn_root.replace(/\\/g, "/"),
        });
      }
    }
    return [];
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
            rawStatus.value = "";
            break;
          default:
            break;
        }
      } else if (!!content.completed) {
        busy.value = false;
        switch (content.job) {
          case "FETCH_DIFFS":
          case "FETCH_UNVERSIONED":
            publishSvnDiffStream({
              id,
              finished: true,
            });
            break;
          default:
            break;
        }
      } else if (!!content.data) {
        switch (content.job) {
          case "FETCH_STATUS":
            rawStatus.value = content.data;
            break;
          case "FETCH_DIFFS":
            publishSvnDiffStream({
              id,
              chunks: parseDiff(content.data),
            });
            break;
          case "FETCH_UNVERSIONED":
            publishSvnDiffStream({
              id,
              unversioned: content.data.split(/\r|\n/g).filter(Boolean),
            });
            break;
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
          title={`Welcome${serverStatus.value}`}
          busy={busy.value}
          source$={settings}
          onFinish={onSettingsChange}
          onFetch={onFetchStatus}
          pickDir={pickDir}
        />
      </Header>
      <Content>
        <SvnDiffProviderContext.Provider value={svnDiffProviderContext}>
          <Diffs status={status.value} settings={settings} />
        </SvnDiffProviderContext.Provider>
      </Content>
    </Layout>
  );
}
