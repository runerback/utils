import type { ReadonlySignal } from "@preact/signals-react";
import { Modal } from "antd";
import SvnLogs from "./SvnLogs";
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { SvnContext } from "../context/svnContext";
import History from "../assets/History.svg?react";
import Close from "../assets/ChromeClose.svg?react";
import { filter } from "rxjs";

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  settings: ReadonlySignal<Settings | undefined>;
  busy: ReadonlySignal<boolean>;
  open: ReadonlySignal<boolean>;
  onClose: () => void;
}) => {
  useSignals();
  const svnLogs = useSignal(Array<SvnLogs>());
  const svnContext = useContext(SvnContext);
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
          <History className="icon" />
          &nbsp;svn logs
        </div>
      }
      width="80vw"
      style={{ maxHeight: "80vh" }}
      closable
      closeIcon={<Close className="icon" onClick={close} />}
      open={props.open.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
    >
      <SvnLogs
        status={props.status}
        logs={svnLogs}
        busy={props.busy}
        settings={props.settings}
      />
    </Modal>
  );
};
