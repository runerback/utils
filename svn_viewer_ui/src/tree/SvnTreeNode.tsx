import { Button } from "antd";
import SvnTreeNodeIcon from "./SvnTreeNodeIcon";
import type { TreeDataNode } from "./SvnTreeModal";
import type { ReadonlySignal } from "@preact/signals-react";
import Info from "../assets/BookAnswers.svg?react";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { SvnInfoContext } from "../context/svnInfoContext";
import { filter } from "rxjs";
import SvnLogTitle from "../logs/SvnLogTitle";

export default (props: {
  node: TreeDataNode;
  openned: boolean;
  busy: ReadonlySignal<boolean>;
  fetch: (node: TreeDataNode) => Promise<string | null | undefined>;
}) => {
  useSignals();
  const svnInfoContext = useContext(SvnInfoContext);
  const fetching = useSignal(false);
  const fetchId = useSignal("");
  const info = useSignal<SvnTreeNodeInfo>();
  const log = useComputed<SvnLog | undefined>(() => {
    const currentInfo = info.value;
    if (!currentInfo || !currentInfo.lastChangedRev) {
      return undefined;
    }
    return {
      revision: currentInfo.lastChangedRev,
      author: currentInfo.lastChangedAuthor,
      timestamp: currentInfo.lastChangedTime,
    };
  });
  useSignalEffect(() => {
    svnInfoContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_INFO"))
      .subscribe((e) => {
        if (e.id === fetchId.value) {
          if (!!e.info) {
            info.value = { ...e.info };
          }
          if (!!e.finished) {
            fetching.value = false;
          }
        }
      });
  });
  const fetch = useCallback(() => {
    if (!fetching.value) {
      fetching.value = true;
      props.fetch(props.node).then((id) => (fetchId.value = !!id ? id : ""));
    }
  }, []);
  return (
    <div className="title">
      <div className="name">
        <SvnTreeNodeIcon node={props.node.data} opened={props.openned} />
        {props.node.data.name}
      </div>
      <div className="operations">
        {!!log.value && (
          <div className="info">
            <SvnLogTitle log={log.value} />
          </div>
        )}
        <Button
          loading={props.busy.value}
          icon={<Info className="icon" />}
          title="Info"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            fetch();
          }}
        />
      </div>
    </div>
  );
};
