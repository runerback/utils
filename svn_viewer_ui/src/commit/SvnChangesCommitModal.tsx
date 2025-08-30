import { Button, Checkbox, Input, List, Modal, Space } from "antd";
import UploadIcon from "../components/icons/UploadIcon";
import modalContext from "../context/modalContext";
import CloseIcon from "../components/icons/CloseIcon";
import { useContext } from "preact/hooks";
import { SvnCommitContext } from "../context/svnCommitContext";
import { useSignal, useSignals } from "@preact/signals-react/runtime";
import { NotifyContext } from "../context/notifyContext";
import "./commit.css";

export default (props: { onCommitChanges: (message: string) => void }) => {
  useSignals();
  const svnCommitContext = useContext(SvnCommitContext);
  const notifyContext = useContext(NotifyContext);
  const checked = useSignal(true);
  const message = useSignal("");
  return (
    <Modal
      title={
        <div>
          <UploadIcon />
          &nbsp;svn commit ({svnCommitContext.files$.value.length} items) . . .
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
              <Button
                type="primary"
                onClick={() => {
                  const msg = message.peek();
                  if (!msg || msg.length < 10) {
                    notifyContext.notify(
                      "Commit message should has at least 10 chars",
                      "warning"
                    );
                  }
                  props.onCommitChanges(msg);
                  svnCommitContext.close();
                }}
              >
                Commit
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
        dataSource={svnCommitContext.files$.value}
        renderItem={(file) => (
          <List.Item>
            <Space direction="horizontal">
              <Checkbox
                checked={checked.value}
                onChange={(e) => (checked.value = e.target.checked)}
              />
              {file}
            </Space>
          </List.Item>
        )}
      />
      {svnCommitContext.files$.value.length > 0 && (
        <Input.TextArea
          className="modal_message"
          rows={4}
          value={message.value}
          onChange={(e) => (message.value = e.currentTarget.value)}
        />
      )}
    </Modal>
  );
};
