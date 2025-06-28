import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { SvnChangelistCard } from "./SvnChangelistCard";
import "./diffs.css";
import { useCallback, useContext, useMemo } from "preact/hooks";
import { SvnSettingsContext } from "../context/settingsContext";
import { filter } from "rxjs";
import { SvnStatusContext } from "../context/svnStatusContext";

export default function (props: {
  fetchLogs: (status: SvnStatusItem) => void;
}) {
  useSignals();
  const status = useSignal(Array<SvnStatus>());
  const svnStatusContext = useContext(SvnStatusContext);
  useSignalEffect(() => {
    console.log(`but now I'm subscribing`);
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
          <SvnChangelistCard
            {...props}
            settings={settings}
            fkey={idx}
            status={states}
            observe={observe}
            unobserve={unobserve}
          />
        ))}
    </div>
  );
}
