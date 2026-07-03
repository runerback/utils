import { Modal } from "antd";
import SvnLogs from "./SvnLogs";
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { SvnDiffContext } from "../context/svnDiffContext";
import { filter } from "rxjs";
import { StatusContext } from "../context/statusContext";
import { lazy, Suspense } from "preact/compat";
import modalContext from "../context/modalContext";
import { SvnLogsContext } from "../context/svnLogsContext";

const History = lazy(() => import("../assets/History.svg?react"));
const CloseIcon = lazy(() => import("../components/icons/CloseIcon"));

export default () => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const svnLogs = useSignal(Array<SvnLogs>());
  const svnDiffContext = useContext(SvnDiffContext);
  const svnLogsContext = useContext(SvnLogsContext);
  useSignalEffect(() => {
    svnDiffContext.stream$
      .pipe(filter((it) => it.job === "FETCH_LOGS"))
      .subscribe((e) => {
        if (svnLogsContext.show$.value) {
          if (!!e.logs && e.logs.length > 0) {
            svnLogs.value = [
              ...e.logs
                .filter((it) => !!it.logs && it.logs.length > 0)
                .map((it) => {
                  if (!it.status) {
                    if (!!svnLogsContext.status$.value) {
                      return {
                        status: svnLogsContext.status$.value,
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
        }
      });
  });
  const close = useCallback(() => {
    svnLogsContext.close();
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
      zIndex={modalContext.SvnDiffLogsModal.priority}
      style={{ maxHeight: "80vh" }}
      closeIcon={
        <Suspense fallback={<img className="icon" />}>
          <CloseIcon />
        </Suspense>
      }
      onCancel={close}
      open={svnLogsContext.show$.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
      footer={null}
    >
      <SvnLogs
        status={svnLogsContext.status$}
        logs={svnLogs}
        busy={statusContext.busy$}
      />
    </Modal>
  );
};
