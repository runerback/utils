import { Button, Collapse, Form, Input, Switch } from "antd";
import { useForm } from "antd/es/form/Form";
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import "./settings.css";
import { debounceTime, Subject } from "rxjs";

const changes$ = new Subject<void>();

export default function (props: {
  busy: boolean;
  title: string;
  source$: ReadonlySignal<Settings | undefined>;
  pickDir: () => void;
  onFinish: (values: Settings) => void;
  onFetch: () => void;
}) {
  useSignals();
  const canFetch = useSignal(false);
  const [svnSettingsForm] = useForm<Settings>();
  useSignalEffect(() => {
    svnSettingsForm.setFieldsValue(props.source$.value ?? {});
    changes$.next();
  });
  useSignalEffect(() => {
    changes$.pipe(debounceTime(200)).subscribe(() => {
      canFetch.value = false;
      svnSettingsForm
        .validateFields()
        .then((values) => {
          canFetch.value = true;
          // props.onFinish(values); // dead end
        })
        .catch((error) => {
          console.log("Form validation failed:", error);
        });
    });
  });
  return (
    <div className="settings">
      <Collapse
        bordered
        className="card"
        defaultActiveKey={[1]}
        items={[
          {
            key: 1,
            label: <b>{props.title}</b>,
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
                onClick={() => props.onFetch()}
              >
                Check Diffs
              </Button>,
            ],
          },
        ]}
      ></Collapse>
    </div>
  );
}
