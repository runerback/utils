import { signal, useSignal, useSignalEffect } from "@preact/signals-react";
import "./app.css";
import network from "./network";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import { Layout, type FormProps } from "antd";
import { useCallback } from "preact/hooks";
import svnparser from "./svnparser";
import Settings from "./settings";
import Diffs from "./diffs";
import SvnDiffProvider, {
  publishSvnDiffStream,
  SvnDiffProviderContext,
} from "./svnDiffProviderContext";
import Content from "./layout/content";
import Header from "./layout/header";

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
  const svnSettings = useSignal<SvnSettings>();
  const rawStatus = useSignal("");
  const status = useComputed(() => {
    if (!!rawStatus.value) {
      const settings = svnSettings.peek();
      if (!!settings && settings.svn_root) {
        return svnparser.parse_status(rawStatus.value, settings);
      }
    }
    return [];
  });
  const busy = useSignal(false);

  const fetchSettings = useCallback((next?: () => void) => {
    busy.value = true;
    network.get_settings().then((settings) => {
      svnSettings.value = !!settings ? { ...settings } : undefined;
      busy.value = false;
      next?.();
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
      const settings = svnSettings.peek();
      if (!!settings && settings.svn_root) {
        return svnparser.parse_diff(rawDiff, {
          svn_root: settings.svn_root.replace(/\\/g, "/"),
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
          default:
            break;
        }
      } else if (!!content.error) {
        console.warn(content.error);
      }
    }
  });

  const pickDir = useCallback(() => {
    network.pick_dir(svnSettings.value?.svn_root).then((dir) => {
      if (!!dir) {
        svnSettings.value = {
          svn_root: dir,
        };
      }
    });
  }, []);

  const onFinish: FormProps<SvnSettings>["onFinish"] = useCallback(
    (values: SvnSettings) => {
      network.update_settings(values).then(() => {
        fetchSettings(() => network.fetch_status());
      });
    },
    []
  );

  return (
    <Layout>
      <Header>
        <Settings
          title={`welcome${serverStatus.value}`}
          busy={busy.value}
          source$={svnSettings}
          onFinish={onFinish}
          pickDir={pickDir}
        />
      </Header>
      <Content>
        <SvnDiffProviderContext.Provider value={svnDiffProviderContext}>
          <Diffs status={status.value} />
        </SvnDiffProviderContext.Provider>
      </Content>
    </Layout>
  );
}
