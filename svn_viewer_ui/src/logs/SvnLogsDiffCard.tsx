import { useComputed, useSignal, useSignalEffect } from "@preact/signals-react";
import { Collapse, Spin } from "antd";
import { useMemo } from "preact/hooks";

export type SvnLogsDiffCardProps = {
  source: string;
  revisions: {
    left: string;
    right: string;
  };
};

export default function (
  props: SvnLogsDiffCardProps & {
    compareStarted: (e: SvnLogsDiffCardProps) => void;
    compareFinished: (e: SvnLogsDiffCardProps) => void;
  }
) {
  const actived = useSignal(true);
  const fetched = useSignal(false);
  const fetching = useComputed(() => !fetched.value && actived.value);
  const key = useMemo(
    () => [props.revisions.left, props.revisions.right].join("-"),
    []
  );
  useSignalEffect(() => {
    if (!fetched.value && fetching.value) {
      props.compareStarted(props);
      // TODO: impl
      setTimeout(() => {
        fetched.value = true;
        props.compareFinished(props);
      }, 1000);
    }
  });
  return (
    <Spin spinning={fetching.value}>
      <Collapse
        bordered
        activeKey={actived.value ? [key] : []}
        onChange={(e) => (actived.value = e.length > 0)}
        items={[
          {
            key: key,
            label: (
              <div>
                <b>{props.revisions.left}</b>
                &nbsp;-&nbsp;
                <b>{props.revisions.right}</b>
              </div>
            ),
            children: [<div>TODO</div>],
          },
        ]}
      />
    </Spin>
  );
}
