import Clipboard from "./clipboard";
import KeyboardContextProvider, {
  KeyboardContext,
} from "../context/keyboardContext";
import { useCallback, useEffect, useMemo } from "preact/hooks";
import { useSignalEffect } from "@preact/signals-react";

export default () => {
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
      <Clipboard />
    </KeyboardContext.Provider>
  );
};
