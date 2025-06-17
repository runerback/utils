import { Button, Collapse, Form, Input, Spin, Switch } from "antd";
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
import "./app.css";
import Refresh from "./assets/Refresh.svg?react";
import DOM from "./assets/DOM.svg?react";

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
  loading: ReadonlySignal<boolean>;
  busy: boolean;
  title: ReadonlySignal<string>;
  source$: ReadonlySignal<Settings | undefined>;
  pickDir: () => void;
  onFinish: (values: Settings) => void;
  onFetchStatus: () => void;
  onFetchTree: () => void;
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
  const fetchStatus = useCallback(() => {
    props.onFetchStatus();
    actived.value = false;
  }, []);
  const title = useComputed(() => {
    if (props.title.value) {
      return `Welcome ${props.title.value}`;
    }
    return "Welcom, but server is 😵";
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
      <Spin spinning={props.loading.value}>
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
                    <span>{label.value}</span>
                  ) : (
                    <div className="collapsed">{label.value}</div>
                  )}
                </div>
              ),
              extra: !actived.value &&
                canFetch.value && [
                  <Button
                    loading={props.busy}
                    icon={
                      <Refresh className={props.busy ? "icon spin" : "icon"} />
                    }
                    title="Check Status"
                    onClick={(e) => {
                      e.stopPropagation();
                      fetchStatus();
                    }}
                  />,
                  <Button
                    icon={<DOM className={"icon"} />}
                    title="Check Tree"
                    onClick={(e) => {
                      e.stopPropagation();
                      props.onFetchTree();
                    }}
                  />,
                ],
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
                  onClick={fetchStatus}
                >
                  Check Status
                </Button>,
              ],
            },
          ]}
        />
      </Spin>
    </div>
  );
}
