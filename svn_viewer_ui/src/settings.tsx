import { Button, Collapse, Form, Input } from "antd";
import { useForm, type FormProps } from "antd/es/form/Form";
import type { ValidateErrorEntity } from "../node_modules/rc-field-form/lib/interface";
import { useSignalEffect, useSignals } from "@preact/signals-react/runtime";
import type { ReadonlySignal } from "@preact/signals-react";
import { useCallback } from "preact/hooks";
import "./settings.css";

export default function (props: {
  busy: boolean;
  title: string;
  source$: ReadonlySignal<SvnSettings | undefined>;
  pickDir: () => void;
  onFinish: (values: SvnSettings) => void;
}) {
  useSignals();
  const [svnSettingsForm] = useForm<SvnSettings>();
  useSignalEffect(() => {
    svnSettingsForm.setFieldsValue(props.source$.value ?? {});
  });
  const onFailed: FormProps<SvnSettings>["onFinishFailed"] = useCallback(
    (errorInfo: ValidateErrorEntity<SvnSettings>) => {
      console.log("Failed:", errorInfo);
    },
    []
  );
  return (
    <div className="settings">
      <Collapse
        bordered
        className="card"
        defaultActiveKey={[1]}
        items={[
          {
            key: 1,
            label: props.title,
            children: [
              <Form
                style={{ minWidth: 800, maxWidth: "100%" }}
                initialValues={{ remember: false }}
                onFinish={props.onFinish}
                onFinishFailed={onFailed}
                autoComplete="off"
                form={svnSettingsForm}
              >
                <Form.Item<SvnSettings>
                  label="Project path"
                  name="svn_root"
                  rules={[
                    {
                      required: true,
                      message: "Please input your project path!",
                    },
                  ]}
                >
                  <Input.Search
                    disabled={props.busy}
                    enterButton="..."
                    onSearch={props.pickDir}
                  />
                </Form.Item>
                <Form.Item label={null}>
                  <Button type="primary" htmlType="submit" loading={props.busy}>
                    Check Diffs
                  </Button>
                </Form.Item>
              </Form>,
            ],
          },
        ]}
      ></Collapse>
    </div>
  );
}
