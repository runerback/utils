import type { NotificationInstance } from "antd/es/notification/interface";
import { notification } from "antd";
import { useCallback } from "preact/hooks";
import Status from "./status";

export default () => {
  const [api, notificationContextHolder] = notification.useNotification({
    placement: "bottomRight",
  });
  const notify = useCallback(
    (
      message: string,
      type: keyof Omit<NotificationInstance, "open" | "destroy">
    ) => {
      switch (type) {
        case "info":
          api.info({ message, duration: 1 });
          break;
        case "success":
          api.success({ message, duration: 1 });
          break;
        case "error":
          api.error({ message, duration: 3 });
          break;
        case "warning":
          api.warning({ message, duration: 2 });
          break;
        default:
          break;
      }
    },
    []
  );

  return (
    <>
      {notificationContextHolder}
      <Status notify={notify} />
    </>
  );
};
