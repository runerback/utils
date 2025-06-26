import { useSignal, type ReadonlySignal } from "@preact/signals-react";
import { useSignalEffect, useSignals } from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { StatusContext } from "../context/statusContext";
import { SvnDiffContext } from "../context/svnDiffContext";
import { filter } from "rxjs";
import { lazy, Suspense } from "preact/compat";
import { Modal } from "antd";
import modalContext from "../context/modalContext";
import SvnRevLogs from "./SvnRevLogs";

const History = lazy(() => import("../assets/History.svg?react"));
const Close = lazy(() => import("../assets/ChromeClose.svg?react"));

export default (props: {
  dir: string;
  open: ReadonlySignal<boolean>;
  onClose: () => void;
}) => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const logs = useSignal(Array<SvnLog>());
  const svnDiffContext = useContext(SvnDiffContext);
  useSignalEffect(() => {
    svnDiffContext.stream$
      .pipe(filter((it) => it.job === "FETCH_LOGS"))
      .subscribe((e) => {
        if (props.open.value) {
          if (!!e.logs && e.logs.length > 0) {
            logs.value = [
              ...e.logs
                .filter((it) => !!it.logs && it.logs.length > 0)
                .flatMap((it) => it.logs ?? [])
                .filter(Boolean)
                .map((it) => it!),
            ];
          }
        }
      });
  });
  const close = useCallback(() => {
    props.onClose();
    logs.value = [];
  }, []);
  return (
    <Modal
      title={
        <div>
          <Suspense fallback={<img className="icon" />}>
            <History className="icon" />
          </Suspense>
          &nbsp;svn logs:&nbsp;<b>{props.dir}</b>
        </div>
      }
      width="80vw"
      zIndex={modalContext.SvnRevLogsModal.priority}
      style={{ maxHeight: "80vh" }}
      closeIcon={
        <Suspense fallback={<img className="icon" />}>
          <Close className="icon" />
        </Suspense>
      }
      onCancel={close}
      open={props.open.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
      footer={null}
    >
      <SvnRevLogs dir={props.dir} logs={logs} busy={statusContext.busy$} />
    </Modal>
  );
};
