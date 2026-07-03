import SvnSettingsContextProvider, {
  SvnSettingsContext,
} from "../context/settingsContext";
import Messages from "./messages";
import { useMemo } from "preact/hooks";

export default () => {
  const svnSettingsContext = useMemo(() => SvnSettingsContextProvider(), []);
  return (
    <SvnSettingsContext.Provider value={svnSettingsContext}>
      <Messages />
    </SvnSettingsContext.Provider>
  );
};
