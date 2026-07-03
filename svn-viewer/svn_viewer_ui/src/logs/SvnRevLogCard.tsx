import { useCallback } from "preact/hooks";
import SvnDiffCardLabel from "../diffs/SvnDiffCardLabel";
import { useSignal, useSignals } from "@preact/signals-react/runtime";
import { Collapse, Spin } from "antd";

export default (props: {
  dir: string;
  log: SvnLog;
  item: SvnRevStatusItem;
}) => {
  useSignals();
  // const fetching = useSignal(false);
  // const fetched = useSignal(false);
  const busy = useSignal(false);
  // const taskId = useSignal("");
  const fetch = useCallback(() => {}, []);
  return (
    <div className="svnrevlogcard">
      <Spin spinning={busy.value}>
        <Collapse
          bordered
          key={props.dir}
          onChange={fetch}
          items={[
            {
              label: (
                <SvnDiffCardLabel status={props.item} hightlight={props.dir} />
              ),
              children: [
                <span>
                  🫨<b>TODO</b>😧
                </span>,
              ],
            },
          ]}
        />
      </Spin>
    </div>
  );
};
