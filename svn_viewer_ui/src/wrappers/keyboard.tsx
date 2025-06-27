import type { NotificationInstance } from "antd/es/notification/interface";
import App from "../app";
import KeyboardContextProvider, {
  KeyboardContext,
} from "../context/keyboardContext";
import { useCallback, useEffect, useMemo } from "preact/hooks";
import { useSignalEffect } from "@preact/signals-react";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const keyboardContext = useMemo(() => KeyboardContextProvider(), []);
  useSignalEffect(() => {
    console.log("ctrl pressing: ", keyboardContext.ctrl$.value);
  });
  const onKeyDown = useCallback((e: KeyboardEvent) => {
    if (e.ctrlKey && e.key === "Control") {
      keyboardContext.onCtrl(true);
    }
  }, []);
  const onKeyUp = useCallback((e: KeyboardEvent) => {
    if (e.key === "Control" && !!keyboardContext.ctrl$.peek()) {
      keyboardContext.onCtrl(false);
    }
  }, []);
  useEffect(() => {
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
    };
  });
  return (
    <KeyboardContext.Provider value={keyboardContext}>
      <App {...props} />
    </KeyboardContext.Provider>
  );
};
