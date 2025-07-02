import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import "./diffs.css";
import { useCallback, useContext, useMemo } from "preact/hooks";
import { SvnSettingsContext } from "../context/settingsContext";
import { filter } from "rxjs";
import { SvnStatusContext } from "../context/svnStatusContext";
import { KeyboardContext } from "../context/keyboardContext";
import { provideSvnLogs } from "../context/svnLogsContext";
import { lazy, Suspense } from "preact/compat";
import { Skeleton } from "antd";

const SvnChangelistCard = lazy(() => import("./SvnChangelistCard"));

export default function () {
  useSignals();
  const status = useSignal(Array<SvnStatus>());
  const svnStatusContext = useContext(SvnStatusContext);
  useSignalEffect(() => {
    svnStatusContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_STATUS"))
      .subscribe((content) => {
        if (content.processing) {
          status.value = [];
        } else if (!!content.status) {
          status.value = content.status;
        }
      });
  });
  const settingsContext = useContext(SvnSettingsContext);
  const settings = useSignal<Settings>();
  useSignalEffect(() => {
    settingsContext.stream$.subscribe((value) => {
      settings.value = value;
    });
  });
  const keyboard = useContext(KeyboardContext);
  const fetchLogs = useCallback((status: SvnStatusItem) => {
    provideSvnLogs(status, keyboard.ctrl$.value);
  }, []);
  const options = useMemo(
    () => ({
      root: document.querySelector(".status"),
      rootMargin: "0px",
      threshold: 0.5,
    }),
    []
  );
  const callback = useCallback<IntersectionObserverCallback>((entries) => {
    entries.forEach((entry) => {
      console.log("IntersectionObserverCallback", {
        target: entry.target,
        isIntersecting: entry.isIntersecting,
      });
    });
  }, []);
  const observer = useMemo(
    () => new IntersectionObserver(callback, options),
    [options]
  );
  const observe = useCallback((target: HTMLElement) => {
    observer.observe(target);
    console.log("observing", target);
  }, []);
  const unobserve = useCallback((target: HTMLElement) => {
    observer.unobserve(target);
    console.log("stop observe", target);
  }, []);
  return (
    <div className="status">
      {status.value
        .filter((it) => it.changes.length > 0)
        .map((states, idx) => (
          <Suspense fallback={<Skeleton loading />}>
            <SvnChangelistCard
              fetchLogs={fetchLogs}
              settings={settings}
              fkey={idx}
              status={states}
              observe={observe}
              unobserve={unobserve}
            />
          </Suspense>
        ))}
    </div>
  );
}
