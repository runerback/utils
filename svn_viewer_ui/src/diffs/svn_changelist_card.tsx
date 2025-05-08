import { useSignals } from "@preact/signals-react/runtime";
import { Collapse } from "antd";
import { SvnDiffCard } from "./svn_diff_card";
import type { Key } from "preact";
import { useRef } from "preact/hooks";
import type { ReadonlySignal } from "@preact/signals-react";

export function SvnChangelistCard(props: {
  key?: Key;
  status: SvnStatus;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
}) {
  useSignals();
  const headerRef = useRef<HTMLSpanElement>(null);
  return (
    <Collapse
      key={props.key}
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
            <SvnDiffCard {...props} key={idx} status={states} />
          )),
        },
      ]}
    ></Collapse>
  );
}
