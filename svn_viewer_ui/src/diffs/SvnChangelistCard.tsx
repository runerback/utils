import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { Collapse } from "antd";
import { SvnDiffCard } from "./SvnDiffCard";
import type { Key } from "preact";
import { useContext, useRef } from "preact/hooks";
import type { ReadonlySignal } from "@preact/signals-react";
import { SvnRevertContext } from "../context/svnRevertContext";

export default (props: {
  fkey?: Key;
  status: SvnStatus;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
  fetchLogs: (status: SvnStatusItem) => void;
}) => {
  useSignals();
  const headerRef = useRef<HTMLSpanElement>(null);
  const changes = useSignal(
    props.status.changes.map((it, idx) => [it, idx] as [SvnStatusItem, number])
  );
  const svnRevertContext = useContext(SvnRevertContext);
  useSignalEffect(() => {
    svnRevertContext.succeed$.subscribe((reverted) => {
      changes.value = changes.value.filter(([it]) => it !== reverted);
    });
  });
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
          children: changes.value.map(([states, idx]) => (
            <SvnDiffCard {...props} fkey={idx} status={states} />
          )),
        },
      ]}
    ></Collapse>
  );
};
