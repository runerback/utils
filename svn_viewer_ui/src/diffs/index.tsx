import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { SvnChangelistCard } from "./SvnChangelistCard";
import "./diffs.css";
import { useCallback, useContext, useMemo } from "preact/hooks";
import { SvnSettingsContext } from "../context/settingsContext";
import { MessageContext } from "../context/messageContext";
import { filter, map } from "rxjs";

export default function (props: {
  fetchLogs: (status: SvnStatusItem) => void;
}) {
  useSignals();
  const messageContext = useContext(MessageContext);
  const status = useSignal(Array<SvnStatus>());
  useSignalEffect(() => {
    messageContext.stream$
      .pipe(
        map((it) => it.content),
        filter(Boolean),
        filter((it) => it.job === "FETCH_STATUS")
      )
      .subscribe((content) => {
        if (!!content.processing) {
          status.value = [];
        } else if (!!content.data) {
          status.value = (content.data as SvnStatus[]) ?? [];
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
