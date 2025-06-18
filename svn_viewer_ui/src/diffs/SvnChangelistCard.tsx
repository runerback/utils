import { useSignals } from "@preact/signals-react/runtime";
import { Collapse } from "antd";
import { SvnDiffCard } from "./SvnDiffCard";
import type { Key } from "preact";
import { useRef } from "preact/hooks";
import type { ReadonlySignal } from "@preact/signals-react";

export function SvnChangelistCard(props: {
  fkey?: Key;
  status: SvnStatus;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
  fetchLogs: (status: SvnStatusItem) => void;
}) {
  useSignals();
  const headerRef = useRef<HTMLSpanElement>(null);
  return (
    <Collapse
      key={props.fkey}
      bordered
      className="statuscard"
      items={[
        {
          label: (
            <span ref={headerRef}>
              <b>{props.status.changelist}</b>
            </span>
          ),
          children: props.status.changes.map((states, idx) => (
            <SvnDiffCard {...props} fkey={idx} status={states} />
          )),
        },
      ]}
    ></Collapse>
  );
}
