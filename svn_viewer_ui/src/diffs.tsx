import { useSignals } from "@preact/signals-react/runtime";
import { SvnChangelistCard } from "./svn_changelist_card";
import "./diffs.css";
import { useCallback, useMemo } from "preact/hooks";

export default function (props: { status: SvnStatus[] }) {
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
    console.log({ IntersectionObserver: entries });
  }, []);
  const observer = useMemo(
    () => new IntersectionObserver(callback, options),
    [options]
  );
  const observe = useCallback((target: HTMLDivElement) => {
    observer.observe(target);
  }, []);
  return (
    <div className="status">
      {props.status.map((states, idx) => (
        <SvnChangelistCard key={idx} status={states} observe={observe} />
      ))}
    </div>
  );
}
