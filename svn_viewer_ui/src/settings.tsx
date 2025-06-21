import { Button, Collapse, Form, Input, Spin, Switch } from "antd";
import { useForm } from "antd/es/form/Form";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import "./settings.css";
import { useCallback, useContext } from "preact/hooks";
import "./app.css";
import Refresh from "./assets/Refresh.svg?react";
import DOM from "./assets/DOM.svg?react";
import {
  SvnSettingsContext,
  createRequest,
  onSettingsFetched,
} from "./context/settingsContext";
import network from "./context/network";
import { StatusContext } from "./context/statusContext";
import type { NotificationInstance } from "antd/es/notification/interface";
import { MessageContext } from "./context/messageContext";
import { filter, map } from "rxjs";

export default function (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
  onFetchTree: () => void;
}) {
  useSignals();
  const statusContext = useContext(StatusContext);
  const serverStatus = useSignal("");
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
  const getSettings = useCallback(() => {
    statusContext.busy();
    network.get_settings().then((value) => {
      if (!!value) {
        onSettingsFetched(value);
      }
      statusContext.idle();
    });
  }, []);
  useSignalEffect(() => {
    if (!serverStatus.value) {
      fetchingServerStatus.value = true;
      fetchServerStatus(10);
    } else {
      getSettings();
    }
  });
  const messageContext = useContext(MessageContext);
  useSignalEffect(() => {
    messageContext.stream$
      .pipe(
        map((it) => it.content),
        filter(Boolean),
        filter((it) => it.job === "FETCH_SETTINGS" && !!it.completed)
      )
      .subscribe(() => {
        getSettings();
      });
  });

  const fetchSettingsId = useSignal("");
  useSignalEffect(() =>
    console.log({ fetchSettingsId: fetchSettingsId.value })
  );
  const settingsContext = useContext(SvnSettingsContext);
  const canFetch = useSignal(false);
  const fetching = useSignal(false);
  const currentSettings = useSignal<Settings>();
  const [svnSettingsForm] = useForm<Settings>();
  const validate = useCallback(() => {
    svnSettingsForm
      .validateFields()
      .then(() => (canFetch.value = true))
      .catch(() => (canFetch.value = false));
  }, []);
  useSignalEffect(() => {
    validate();
  });
  const fetchSettings = useCallback(() => {
    svnSettingsForm
      .validateFields()
      .then((values) => {
        fetching.value = true;
        canFetch.value = true;
        currentSettings.value = undefined;
        createRequest(values);
      })
      .catch((error) => {
        props.notify(error.toString(), "error");
        canFetch.value = false;
      });
  }, []);
  const actived = useSignal(true);
  const fetchStatus = useCallback(() => {
    statusContext.busy();
    network.fetch_status();
    actived.value = false;
  }, []);
  const pickRootDir = useCallback(() => {
    settingsContext.pickDir().then((dir) => {
      if (!!dir) {
        svnSettingsForm.setFieldValue("svn_root", dir);
        validate();
      }
    });
  }, []);
  useSignalEffect(() => {
    settingsContext.request$.subscribe((request) => {
      statusContext.busy();
      settingsContext.provide(request).then((id) => {
        fetchSettingsId.value = !!id ? id : "";
      });
    });
    settingsContext.stream$.subscribe((settings) => {
      svnSettingsForm.setFieldsValue(settings);
      currentSettings.value = settings;
      fetching.value = false;
      fetchStatus();
    });
  });
  const title = useComputed(() => {
    if (serverStatus.value) {
      return `Welcome ${serverStatus.value}`;
    }
    return "Welcom, but server is 😵";
  });
  const label = useComputed(() => {
    if (actived.value) {
      return title.value;
    }
    if (!!currentSettings.value) {
      return currentSettings.value.svn_root;
    }
    return title.value;
  });
  return (
    <div className="settings">
      <Spin spinning={fetchingServerStatus.value}>
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
                !fetching.value &&
                !!currentSettings.value && [
                  <Button
                    loading={statusContext.busy$.value}
                    icon={
                      <Refresh
                        className={
                          statusContext.busy$.value ? "icon spin" : "icon"
                        }
                      />
                    }
                    title="Check Status"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      fetchStatus();
                    }}
                  />,
                  <Button
                    icon={<DOM className={"icon"} />}
                    title="Check Tree"
                    onClick={(e) => {
                      e.preventDefault();
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
                  onValuesChange={fetchSettings}
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
                      disabled={statusContext.busy$.value}
                      enterButton="..."
                      onSearch={pickRootDir}
                    />
                  </Form.Item>
                  <Form.Item<Settings> label="Use dark theme" name="dark_theme">
                    <Switch />
                  </Form.Item>
                </Form>,
                <Button
                  type="primary"
                  size="small"
                  loading={statusContext.busy$.value || fetching.value}
                  disabled={!canFetch.value || fetching.value}
                  onClick={fetchSettings}
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
