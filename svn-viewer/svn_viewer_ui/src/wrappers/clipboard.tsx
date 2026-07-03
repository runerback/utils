import { useCallback, useContext, useMemo } from "preact/hooks";
import ClipboardContextProvider, {
  ClipboardContext,
} from "../context/clipboardContext";
import { NotifyContext } from "../context/notifyContext";
import App from "../app";

export default () => {
  const notificationContext = useContext(NotifyContext);
  const copied = useCallback(() => {
    notificationContext.notify("Copied", "success");
  }, []);
  const clipboardContext = useMemo(() => ClipboardContextProvider(copied), []);
  return (
    <ClipboardContext.Provider value={clipboardContext}>
      <App />
    </ClipboardContext.Provider>
  );
};
