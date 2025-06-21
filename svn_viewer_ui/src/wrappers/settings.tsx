import type { NotificationInstance } from "antd/es/notification/interface";
import SvnSettingsContextProvider, {
  SvnSettingsContext,
} from "../context/settingsContext";
import Messages from "./messages";
import { useMemo } from "preact/hooks";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const svnSettingsContext = useMemo(() => SvnSettingsContextProvider(), []);
  return (
    <SvnSettingsContext.Provider value={svnSettingsContext}>
      <Messages {...props} />
    </SvnSettingsContext.Provider>
  );
};
