import {
  Signal,
  signal,
  useSignal,
  useSignalEffect,
} from "@preact/signals-react";
import "./app.css";
import network from "./network";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import type { FormProps } from "antd";
import { Button, Card, Form, Input } from "antd";
import type { ValidateErrorEntity } from "../node_modules/rc-field-form/lib/interface";
import { useCallback } from "preact/hooks";
import { useForm } from "antd/es/form/Form";
import svnparser from "./svnparser";
import { SvnChangelistCard } from "./svn_changelist_card";

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
const jobs: Record<string, Signal<Job>> = {};
const AnonymousJobId = "<anonymous>";

export function App() {
  useSignals();
  const serverStatus = useSignal("!");
  const svnSettings = useSignal<SvnSettings>();
  const [svnSettingsForm] = useForm<SvnSettings>();
  const rawStatus = useSignal("");
  const status = useComputed(() => {
    if (!!rawStatus.value) {
      return svnparser.parse_status(rawStatus.value);
    }
    return [];
  });
  // const rawdiff = useSignal("");
  // const diffs = useComputed(() => {
  //   if (!!rawdiff.value) {
  //     const settings = svnSettings.peek();
  //     if (!!settings && settings.svn_root) {
  //       return svnparser.parse_diff(rawdiff.value, {
  //         svn_root: settings.svn_root.replace(/\\/g, "/"),
  //       });
  //     }
  //   }
  //   return [];
  // });
  const busy = useSignal(false);

  const fetchJob = useCallback((status: Job, id?: string) => {
    const jobId = id ?? AnonymousJobId;
    const job = jobs[jobId];
    if (!!job) {
      job.value = status;
    } else {
      jobs[jobId] = useSignal(status);
    }
  }, []);

  const fetchSettings = useCallback((next?: () => void) => {
    fetchJob("FETCH_SETTINGS");
    network.get_settings().then((settings) => {
      svnSettings.value = !!settings ? { ...settings } : undefined;
      if (!next) {
        fetchJob("IDLE");
      } else {
        next();
      }
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

  useSignalEffect(() => {
    svnSettingsForm.setFieldsValue(svnSettings.value ?? {});
  });

  // const diffs =
  // useMemo<Record<string, Signal<Chunk1 | undefined>>>(() => {
  //   const result: Record<string, Signal<Chunk1 | undefined>> = {};
  //   props.status.changes.forEach((changes) => {
  //     result[changes.source] = useSignal<Chunk1>();
  //   });
  //   return result;
  // }, []);

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
        const job = jobs[id ?? AnonymousJobId];
        switch (job.value) {
          case "FETCH_STATUS":
            rawStatus.value = "";
            break;
          case "FETCH_DIFFS":
            break;
          default:
            break;
        }
      } else if (!!content.completed) {
        busy.value = false;
      } else if (!!content.data) {
        const job = jobs[id ?? AnonymousJobId];
        switch (job.value) {
          case "FETCH_STATUS":
            rawStatus.value = content.data;
            break;
          default:
            break;
        }
        if (!busy.value) {
          job.value = "IDLE";
        }
      } else if (!!content.error) {
        console.warn(content.error);
      }
    }
  });

  const diffProvider = useCallback((status: SvnStatusItem) => {
    network.fetch_diff(status.source).then((id) => fetchJob("IDLE", id));
  }, []);

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
        fetchSettings(() => {
          fetchJob("FETCH_STATUS");
          network.fetch_status();
        });
      });
    },
    []
  );

  const onFinishFailed: FormProps<SvnSettings>["onFinishFailed"] = useCallback(
    (errorInfo: ValidateErrorEntity<SvnSettings>) => {
      console.log("Failed:", errorInfo);
    },
    []
  );

  return (
    <div>
      <div className="settings">
        <Card title={`welcome${serverStatus.value}`}>
          <Form
            style={{ minWidth: 800, maxWidth: "100%" }}
            initialValues={{ remember: false }}
            onFinish={onFinish}
            onFinishFailed={onFinishFailed}
            autoComplete="off"
            form={svnSettingsForm}
          >
            <Form.Item<SvnSettings>
              label="Project path"
              name="svn_root"
              rules={[
                { required: true, message: "Please input your project path!" },
              ]}
            >
              <Input.Search
                disabled={busy.value}
                enterButton="..."
                onSearch={pickDir}
              />
            </Form.Item>
            <Form.Item label={null}>
              <Button type="primary" htmlType="submit" loading={busy.value}>
                Check Diffs
              </Button>
            </Form.Item>
          </Form>
        </Card>
      </div>
      <div className="status">
        {status.value.map((status, idx) => (
          <SvnChangelistCard
            key={idx}
            status={status}
            diffs={{}} // TODO
            diffProvider={diffProvider}
          />
        ))}
      </div>
    </div>
  );
}
