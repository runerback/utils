import { Button, Collapse, Form, Input, Switch } from "antd";
import { useForm } from "antd/es/form/Form";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import "./settings.css";
import { debounceTime, Subject } from "rxjs";
import { useCallback } from "preact/hooks";
import { ReloadOutlined } from "@ant-design/icons";

const changes$ = new Subject<void>();
const hasChanged = (a?: Settings, b?: Settings) => {
  if (!a || !b) {
    return false;
  }
  if (a.svn_root !== b.svn_root) {
    return true;
  }
  if (Boolean(a.dark_theme) !== Boolean(b.dark_theme)) {
    return true;
  }
  return false;
};

export default function (props: {
  busy: boolean;
  title: ReadonlySignal<string>;
  source$: ReadonlySignal<Settings | undefined>;
  pickDir: () => void;
  onFinish: (values: Settings) => void;
  onFetch: () => void;
}) {
  useSignals();
  const fetched = useSignal(false);
  useSignalEffect(() => console.log({ fetched: fetched.value }));
  const canFetch = useSignal(false);
  const [svnSettingsForm] = useForm<Settings>();
  useSignalEffect(() => {
    svnSettingsForm.setFieldsValue(props.source$.value ?? {});
    changes$.next();
  });
  useSignalEffect(() => {
    changes$.pipe(debounceTime(200)).subscribe(() => {
      const shouldFetch = fetched.peek() === false;
      canFetch.value = false;
      svnSettingsForm
        .validateFields()
        .then((values) => {
          const changed = hasChanged(values, props.source$.peek());
          console.log("form values changed", { shouldFetch, changed, values });
          canFetch.value = true;
          if (shouldFetch || changed) {
            props.onFinish(values);
            fetched.value = true;
          }
        })
        .catch((error) => {
          console.log("Form validation failed:", error);
        });
    });
  });
  const actived = useSignal(true);
  const fetch = useCallback(() => {
    props.onFetch();
    actived.value = false;
  }, []);
  const title = useComputed(() => {
    if (props.title.value) {
      return `Welcome ${props.title.value}`;
    }
    return "Welcom, but 😵";
  });
  const label = useComputed(() => {
    if (actived.value) {
      return title.value;
    }
    if (!!props.source$.value?.svn_root) {
      return props.source$.value.svn_root;
    }
    return title.value;
  });
  return (
    <div className="settings">
      <Collapse
        bordered
        className="card"
        activeKey={actived.value ? [1] : []}
        onChange={(e) => (actived.value = e.length > 0)}
        items={[
          {
            key: 1,
            label: (
              <div className="header">
                {actived.value ? (
                  <b>{label.value}</b>
                ) : (
                  <div className="collapsed">
                    <b>{label.value}</b>
                  </div>
                )}
              </div>
            ),
            extra: !actived.value && canFetch.value && (
              <Button
                loading={props.busy}
                icon={<ReloadOutlined spin={props.busy} />}
                title="Check Status"
                onClick={(e) => {
                  e.stopPropagation();
                  fetch();
                }}
              />
            ),
            children: [
              <Form
                style={{ minWidth: 800, maxWidth: "100%", textAlign: "left" }}
                initialValues={{ remember: false }}
                autoComplete="off"
                form={svnSettingsForm}
                onValuesChange={() => changes$.next()}
              >
                <Form.Item<Settings>
                  label="Project path"
                  name="svn_root"
                  rules={[
                    {
                      required: true,
                      message: "Please choose your project path!",
                    },
                  ]}
                >
                  <Input.Search
                    readOnly
                    disabled={props.busy}
                    enterButton="..."
                    onSearch={props.pickDir}
                  />
                </Form.Item>
                <Form.Item<Settings> label="Use dark theme" name="dark_theme">
                  <Switch />
                </Form.Item>
              </Form>,
              <Button
                type="primary"
                size="small"
                loading={props.busy}
                disabled={!canFetch.value}
                onClick={fetch}
              >
                Check Status
              </Button>,
            ],
          },
        ]}
      ></Collapse>
    </div>
  );
}
