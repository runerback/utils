import type { ReadonlySignal } from "@preact/signals-react";
import { Modal } from "antd";
import SvnLogs from "./SvnLogs";
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { SvnDiffContext } from "../context/svnDiffContext";
import Close from "../assets/ChromeClose.svg?react";
import { filter } from "rxjs";
import { SvnSettingsContext } from "../context/settingsContext";
import { StatusContext } from "../context/statusContext";
import { lazy, Suspense } from "preact/compat";

const History = lazy(() => import("../assets/History.svg?react"));

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  open: ReadonlySignal<boolean>;
  onClose: () => void;
}) => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const settingsContext = useContext(SvnSettingsContext);
  const settings = useSignal<Settings>();
  useSignalEffect(() => {
    settingsContext.stream$.subscribe((value) => {
      settings.value = value;
    });
  });
  const svnLogs = useSignal(Array<SvnLogs>());
  const svnContext = useContext(SvnDiffContext);
  useSignalEffect(() => {
    svnContext.stream$
      .pipe(filter((it) => it.job === "FETCH_LOGS"))
      .subscribe((e) => {
        if (!!e.logs && e.logs.length > 0) {
          svnLogs.value = [
            ...e.logs
              .filter((it) => !!it.logs && it.logs.length > 0)
              .map((it) => {
                if (!it.status) {
                  if (!!props.status.value) {
                    return {
                      status: props.status.value,
                      logs: it.logs ?? [],
                    };
                  }
                } else {
                  return {
                    status: it.status,
                    logs: it.logs ?? [],
                  };
                }
              })
              .filter(Boolean)
              .map((it) => it!),
          ];
        }
      });
  });
  const close = useCallback(() => {
    props.onClose();
    svnLogs.value = [];
  }, []);
  return (
    <Modal
      title={
        <div>
          <Suspense fallback={<img className="icon" />}>
            <History className="icon" />
          </Suspense>
          &nbsp;svn logs
        </div>
      }
      width="80vw"
      style={{ maxHeight: "80vh" }}
      closeIcon={<Close className="icon" />}
      onCancel={close}
      open={props.open.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
    >
      <SvnLogs
        status={props.status}
        logs={svnLogs}
        busy={statusContext.busy$}
        settings={settings}
      />
    </Modal>
  );
};
