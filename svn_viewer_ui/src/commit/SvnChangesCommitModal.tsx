import { Button, Checkbox, Input, List, Modal, Space, Switch } from "antd";
import UploadIcon from "../components/icons/UploadIcon";
import modalContext from "../context/modalContext";
import CloseIcon from "../components/icons/CloseIcon";
import { useCallback, useContext } from "preact/hooks";
import { SvnCommitContext } from "../context/svnCommitContext";
import {
  useComputed,
  useSignal,
  useSignals,
} from "@preact/signals-react/runtime";
import { NotifyContext } from "../context/notifyContext";
import "./commit.css";
import { Signal, signal } from "@preact/signals-react";

export default (props: {
  onCommitChanges: (params: {
    files: string[];
    message: string;
    commit?: boolean;
  }) => void;
}) => {
  useSignals();
  const svnCommitContext = useContext(SvnCommitContext);
  const notifyContext = useContext(NotifyContext);
  const message = useSignal("");
  const clname = useSignal("");
  const committing = useSignal(false);
  const files = useComputed(() => {
    return svnCommitContext.files$.value.map(
      (it) => [it, signal(true)] as [string, Signal<boolean>]
    );
  });
  const confirm = useCallback(() => {
    const msg = message.value;
    if (committing.value && (!msg || msg.length < 10)) {
      notifyContext.notify(
        "Commit message should has at least 10 chars",
        "warning"
      );
    }
    props.onCommitChanges({
      files: files.value
        .filter(([, checked]) => checked.value)
        .map(([file]) => file),
      message: msg,
      commit: committing.value,
    });
    svnCommitContext.clear();
    svnCommitContext.close();
  }, []);
  return (
    <Modal
      title={
        <div className="modal_title">
          <UploadIcon />
          <span>
            svn commit (
            {files.value.filter(([, checked]) => checked.value).length} items) .
            . .
          </span>
          <div className="modal_switch">
            <Switch
              value={committing.value}
              onChange={(e) => (committing.value = e)}
              checkedChildren="Commit"
              unCheckedChildren="Add To ChangeList"
              defaultChecked
            />
          </div>
        </div>
      }
      width="60vw"
      height="80vh"
      zIndex={modalContext.SvnCommitModal.priority}
      closable
      closeIcon={<CloseIcon />}
      onCancel={() => svnCommitContext.close()}
      open={svnCommitContext.show$.value}
      footer={
        svnCommitContext.files$.value.length > 0 && (
          <div className="modal_footer">
            <Space>
              <Button type="primary" onClick={confirm}>
                Confirm
              </Button>
              <Button onClick={() => svnCommitContext.close()}>Cancel</Button>
            </Space>
          </div>
        )
      }
    >
      <List
        className={
          svnCommitContext.files$.value.length === 0
            ? "modal_empty_list"
            : "modal_list"
        }
        dataSource={files.value}
        renderItem={([file, checked]) => (
          <List.Item>
            <Space direction="horizontal" wrap>
              <Checkbox
                checked={checked.value}
                onChange={(e) => (checked.value = e.target.checked)}
              >
                {file}
              </Checkbox>
            </Space>
          </List.Item>
        )}
      />
      {svnCommitContext.files$.value.length > 0 &&
        (committing.value ? (
          <Input.TextArea
            className="modal_message"
            rows={4}
            value={message.value}
            placeholder="Enter commit messages . . ."
            onChange={(e) => (message.value = e.currentTarget.value)}
          />
        ) : (
          <Input
            value={clname.value}
            placeholder="Enter changelist name . . ."
            onChange={(e) => (clname.value = e.currentTarget.value)}
          />
        ))}
    </Modal>
  );
};
