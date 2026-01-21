import {
  Badge,
  Button,
  Collapse,
  Form,
  Input,
  Space,
  Spin,
  Switch,
} from "antd";
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
import {
  SvnSettingsContext,
  createRequest,
  onSettingsFetched,
  setCurrent,
} from "./context/settingsContext";
import network from "./context/network";
import { StatusContext } from "./context/statusContext";
import { MessageContext } from "./context/messageContext";
import { filter, map } from "rxjs";
import { lazy, Suspense } from "preact/compat";
import { NotifyContext } from "./context/notifyContext";
import { provideSvnStatus } from "./context/svnStatusContext";
import { KeyboardContext } from "./context/keyboardContext";
import { SvnCommitContext } from "./context/svnCommitContext";

const Refresh = lazy(() => import("./assets/Refresh.svg?react"));
const DOM = lazy(() => import("./assets/DOM.svg?react"));
const UploadIcon = lazy(() => import("./components/icons/UploadIcon"));

const SettingsHeader = (props: { settings: Settings }) => {
  const openRepo = useCallback((url: string) => {
    network.open_repo_browser().then((res) => {
      if (!res || !res.succeed) {
        window.open(url);
      }
    });
  }, []);
  return (
    <div className="headerTitle">
      <b>{props.settings.svn_root}</b>
      {!!props.settings.svn_repo && (
        <span>
          &nbsp;&nbsp;&nbsp;(
          <a
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              openRepo(props.settings.svn_repo!);
            }}
          >
            {props.settings.svn_repo}
          </a>
          {!!props.settings.svn_rev && (
            <span>
              :<b>{props.settings.svn_rev}</b>
            </span>
          )}
          )
        </span>
      )}
    </div>
  );
};

const UploadButtonIcon = () => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const svnCommitContext = useContext(SvnCommitContext);
  return svnCommitContext.pendingCount$.value > 0 ? (
    <Badge count={svnCommitContext.pendingCount$.value} overflowCount={99}>
      <UploadIcon
        className={statusContext.busy$.value ? "icon p5 spin" : "icon p5"}
      />
    </Badge>
  ) : (
    <Suspense fallback={<img className="icon" />}>
      <UploadIcon
        className={statusContext.busy$.value ? "icon p5 spin" : "icon p5"}
      />
    </Suspense>
  );
};

export default function (props: {
  onFetchTree: () => void;
  onCommitting: () => void;
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
        filter((it) => it.job === "FETCH_SETTINGS" && !!it.completed),
      )
      .subscribe(() => {
        getSettings();
      });
  });

  const fetchSettingsId = useSignal("");
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
  const notifyContext = useContext(NotifyContext);
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
        notifyContext.notify(error.toString(), "error");
        canFetch.value = false;
      });
  }, []);
  const keyboardContext = useContext(KeyboardContext);
  const actived = useSignal(true);
  const fetchStatus = useCallback(() => {
    provideSvnStatus(keyboardContext.ctrl$.value);
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
  useSignalEffect(() => {
    setCurrent(currentSettings.value);
  });
  const title = useComputed(() => {
    if (serverStatus.value) {
      return `Welcome ${serverStatus.value}`;
    }
    return "Welcom, but server is 😵";
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
                    <span>
                      <b>{title.value}</b>
                    </span>
                  ) : !currentSettings.value ? (
                    <div className="collapsed">
                      <b>{title.value}</b>
                    </div>
                  ) : (
                    <div className="collapsed">
                      <SettingsHeader settings={currentSettings.value} />
                    </div>
                  )}
                </div>
              ),
              extra: !actived.value &&
                !fetching.value &&
                !!currentSettings.value && (
                  <Space>
                    <Button
                      loading={statusContext.busy$.value}
                      icon={
                        <Suspense fallback={<img className="icon" />}>
                          <Refresh
                            className={
                              statusContext.busy$.value
                                ? "icon p5 spin"
                                : "icon p5"
                            }
                          />
                        </Suspense>
                      }
                      title="Check Status"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        fetchStatus();
                      }}
                    />
                    <Button
                      icon={
                        <Suspense fallback={<img className="icon" />}>
                          <DOM
                            className={
                              statusContext.busy$.value
                                ? "icon p5 spin"
                                : "icon p5"
                            }
                          />
                        </Suspense>
                      }
                      title="Check Tree"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        props.onFetchTree();
                      }}
                    />
                    <Button
                      icon={<UploadButtonIcon />}
                      title="Upload"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        props.onCommitting();
                      }}
                    />
                  </Space>
                ),
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
                      onClick={pickRootDir}
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
