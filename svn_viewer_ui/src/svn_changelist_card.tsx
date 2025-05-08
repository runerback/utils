import { useSignalEffect, useSignals } from "@preact/signals-react/runtime";
import { Collapse } from "antd";
import { SvnDiffCard } from "./svn_diff_card";
import type { Key } from "preact";

export function SvnChangelistCard(props: {
  key?: Key;
  status: SvnStatus;
  observe: (target: HTMLDivElement) => void;
}) {
  useSignals();
  useSignalEffect(() => {
    console.log({ status: props.status });
  });
  return (
    <Collapse
      key={props.key}
      bordered
      className="statuscard"
      items={[
        {
          label: props.status.changelist,
          children: props.status.changes.map((states, idx) => (
            <SvnDiffCard key={idx} status={states} observe={props.observe} />
          )),
        },
      ]}
    ></Collapse>
  );
}
