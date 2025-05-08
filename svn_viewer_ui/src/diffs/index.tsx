import { useSignals } from "@preact/signals-react/runtime";
import { SvnChangelistCard } from "./svn_changelist_card";
import "./diffs.css";
import { useCallback, useMemo } from "preact/hooks";
import type { ReadonlySignal } from "@preact/signals-react";

export default function (props: {
  status: SvnStatus[];
  settings: ReadonlySignal<Settings | undefined>;
}) {
  useSignals();
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
      {props.status.map((states, idx) => (
        <SvnChangelistCard
          {...props}
          key={idx}
          status={states}
          observe={observe}
          unobserve={unobserve}
        />
      ))}
    </div>
  );
}
