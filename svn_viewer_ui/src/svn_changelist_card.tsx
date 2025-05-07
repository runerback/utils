import { useSignalEffect, useSignals } from "@preact/signals-react/runtime";
import { Collapse } from "antd";
import "./svn_changelist_card.css";
import { SvnDiffCard } from "./svn_diff_card";
import type { Key } from "preact";
import type { Signal } from "@preact/signals-react";

export function SvnChangelistCard(props: {
  key?: Key;
  status: SvnStatus;
  diffs: Record<string, Signal<Chunk1 | undefined>>;
  diffProvider: (status: SvnStatusItem) => void;
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
          children: props.status.changes.map((status, idx) => (
            <SvnDiffCard
              key={idx}
              status={status}
              diffs={props.diffs[status.source]}
              provider={props.diffProvider}
            />
          )),
        },
      ]}
    ></Collapse>
  );
}
