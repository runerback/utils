import type { NotificationInstance } from "antd/es/notification/interface";
import SvnSettingsContextProvider, {
  SvnSettingsContext,
} from "../context/settingsContext";
import App from "../app";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const svnSettingsContext = SvnSettingsContextProvider();
  return (
    <SvnSettingsContext.Provider value={svnSettingsContext}>
      <App {...props} />
    </SvnSettingsContext.Provider>
  );
};
