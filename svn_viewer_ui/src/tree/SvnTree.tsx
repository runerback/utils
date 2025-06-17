import type { ReadonlySignal } from "@preact/signals-react";
import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { useCallback, useContext } from "preact/hooks";
import { SvnTreeContext } from "../context/svnTreeContext";
import { filter } from "rxjs";

export default (props: {
  busy: ReadonlySignal<boolean>;
  root?: string;
  onFetched: (id: string) => void;
  onChange: (root: string | undefined, nodes: SvnTreeNode[]) => void;
}) => {
  useSignals();
  const svnTreeContext = useContext(SvnTreeContext);
  const fetched = useSignal(false);
  const fetching = useSignal(false);
  const fetchId = useSignal("");
  const nodes = useSignal(Array<SvnTreeNode>());
  useSignalEffect(() => {
    svnTreeContext.stream$
      .pipe(
        filter(
          (it) =>
            !!it &&
            !!it.id &&
            it.job === "FETCH_TREE" &&
            it.id === fetchId.value
        )
      )
      .subscribe((e) => {
        if (!!e.nodes && e.nodes.length > 0) {
          nodes.value = e.nodes;
        }
        if (!!e.finished) {
          fetching.value = false;
          fetched.value = true;
          props.onFetched(e.id);
        }
      });
  });
  const fetch = useCallback(() => {
    fetching.value = true;
    svnTreeContext.provide(props.root).then((id) => {
      if (!!id) {
        fetchId.value = id;
      } else {
        fetchId.value = "";
      }
    });
  }, []);
  useSignalEffect(() => {
    if (!fetched.value && !fetching.value) {
      fetch();
    }
  });
  useSignalEffect(() => {
    props.onChange(props.root, nodes.value);
  });
  return <></>;
};
