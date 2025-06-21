import { useMemo } from "preact/hooks";
import StatusContextProvider, { StatusContext } from "../context/statusContext";
import Signals from "./signals";
import type { NotificationInstance } from "antd/es/notification/interface";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const statusContext = useMemo(() => StatusContextProvider(), []);
  return (
    <StatusContext.Provider value={statusContext}>
      <Signals {...props} />
    </StatusContext.Provider>
  );
};
