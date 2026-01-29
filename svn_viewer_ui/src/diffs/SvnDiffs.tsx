import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import "./diffs.css";
import {
  useCallback,
  useContext,
  useMemo,
  type MutableRef,
} from "preact/hooks";
import { SvnSettingsContext } from "../context/settingsContext";
import { filter } from "rxjs";
import { SvnStatusContext } from "../context/svnStatusContext";
import { KeyboardContext } from "../context/keyboardContext";
import { provideSvnLogs } from "../context/svnLogsContext";
import { IntersectionContext } from "../context/intersectionContext";
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
    [],
  );
  const intersectionContext = useContext(IntersectionContext);
  const observer = useMemo(
    () =>
      new IntersectionObserver(intersectionContext.ObserverCallback$, options),
    [options],
  );
  const observe = useCallback(
    (target: MutableRef<HTMLElement>, callback: (active: boolean) => void) => {
      if (target.current) {
        observer.observe(target.current);
        intersectionContext.register(
          target.current.textContent,
          target,
          callback,
        );
        console.log("observing", target.current);
      }
    },
    [],
  );
  const unobserve = useCallback((target: MutableRef<HTMLElement>) => {
    if (target.current) {
      observer.unobserve(target.current);
      intersectionContext.unregister(target.current.textContent);
      console.log("stop observe", target.current);
    }
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
