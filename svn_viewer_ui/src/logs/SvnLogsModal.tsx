import type { ReadonlySignal } from "@preact/signals-react";
import { Modal } from "antd";
import SvnLogs from "./SvnLogs";
import { useSignals } from "@preact/signals-react/runtime";
import History from "../assets/History.svg?react";
import Close from "../assets/ChromeClose.svg?react";

export default (props: {
  status: ReadonlySignal<SvnStatusItem | undefined>;
  settings: ReadonlySignal<Settings | undefined>;
  busy: ReadonlySignal<boolean>;
  open: ReadonlySignal<boolean>;
  onClose: () => void;
}) => {
  useSignals();
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
      closeIcon={<Close className="icon" onClick={() => props.onClose()} />}
      open={props.open.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
    >
      <SvnLogs
        status={props.status}
        busy={props.busy}
        settings={props.settings}
      />
    </Modal>
  );
};
