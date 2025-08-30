import {
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { Modal, Skeleton, Tree } from "antd";
import DOM from "../assets/DOM.svg?react";
import { useCallback, useContext } from "preact/hooks";
import "./tree.css";
import type { EventDataNode, FieldDataNode } from "rc-tree/lib/interface";
import type { Key } from "readline";
import Up from "../assets/CaretUpSolid8.svg?react";
import Down from "../assets/CaretDown8.svg?react";
import { SignalPromiseContext } from "../context/signalPromiseContext";
import { SvnTreeContext } from "../context/svnTreeContext";
import { filter } from "rxjs";
import SvnTreeNode from "./SvnTreeNode";
import { StatusContext } from "../context/statusContext";
import modalContext from "../context/modalContext";
import { KeyboardContext } from "../context/keyboardContext";
import { provideSvnLogs } from "../context/svnLogsContext";
import CloseIcon from "../components/icons/CloseIcon";

const treeNodesLookup: Record<string, SvnTreeNode[]> = {};
export type TreeDataNode = FieldDataNode<{
  key: Key;
  data: SvnTreeNode;
  title?: React.ReactNode;
}>;
const buildTree = (root: string): TreeDataNode[] => {
  const nodes = treeNodesLookup[root];
  if (!nodes || nodes.length === 0) {
    return [];
  }
  return nodes
    .sort((a, b) => {
      const kind = a.kind.localeCompare(b.kind);
      if (kind !== 0) {
        return kind;
      }
      return a.name.localeCompare(b.name);
    })
    .map((node) => {
      const next =
        root === "/" ? root + node.name : [root, node.name].join("/");
      return {
        key: next,
        title: node.name,
        data: node,
        isLeaf: !node.expandable,
        children: node.kind === "DIR" ? buildTree(next) : [],
      } as TreeDataNode;
    });
};

export default (props: { fetchRevLogs: (dir: string) => void }) => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const treeNodes = useSignal(Array<TreeDataNode>());
  const loadingRoot = useSignal("/");
  const expandedKeys = useSignal(Array<string>());
  const handleNodeClick = useCallback(
    (
      _: React.MouseEvent<HTMLSpanElement>,
      node: EventDataNode<TreeDataNode>
    ) => {
      if (node.data.kind === "DIR") {
        if (node.expanded) {
          const next = expandedKeys.peek();
          next.splice(next.indexOf(node.key), 1);
          expandedKeys.value = [...next];
        } else {
          expandedKeys.value = [...expandedKeys.value, node.key];
          if (!node.loaded) {
            node.loading = true;
            setTimeout(() => {
              node.loading = false;
              node.loaded = true;
            }, 1000);
          }
        }
      }
    },
    []
  );
  const svnTreeContext = useContext(SvnTreeContext);
  const fetching = useSignal(false);
  const fetchId = useSignal("");
  useSignalEffect(() => {
    svnTreeContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_TREE"))
      .subscribe((e) => {
        if (svnTreeContext.show$.value) {
          if (e.id === fetchId.value) {
            if (!!e.nodes && e.nodes.length > 0) {
              treeNodesLookup[loadingRoot.value] = e.nodes;
              treeNodes.value = buildTree("/");
            }
            if (!!e.finished) {
              fetching.value = false;
            }
          }
        }
      });
  });
  useSignalEffect(() => {
    if (svnTreeContext.show$.value) {
      svnTreeContext.provide(loadingRoot.value).then((id) => {
        if (!!id) {
          fetchId.value = id;
        } else {
          fetchId.value = "";
        }
      });
    }
  });
  const signalPromiseContext = useContext(SignalPromiseContext);
  const loadSubNodes = useCallback(async (e: EventDataNode<TreeDataNode>) => {
    if (!fetching.value) {
      loadingRoot.value = e.key;
      fetching.value = true;
      try {
        await signalPromiseContext.provide(fetching, (value) => !value);
      } catch (message) {
        console.error(message);
      }
    }
  }, []);

  const close = useCallback(() => {
    svnTreeContext.close();
    treeNodes.value = [];
  }, []);

  const keyboard = useContext(KeyboardContext);
  const fetchLogs = useCallback((status: SvnStatusItem) => {
    provideSvnLogs(status, keyboard.ctrl$.value);
  }, []);

  return (
    <Modal
      title={
        <div>
          <DOM className="icon" />
          &nbsp;svn tree
        </div>
      }
      width="80vw"
      height="80vh"
      zIndex={modalContext.SvnTreeModal.priority}
      closable
      closeIcon={<CloseIcon />}
      onCancel={close}
      open={svnTreeContext.show$.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
      footer={null}
    >
      {treeNodes.value.length === 0 && <Skeleton loading />}
      <Tree
        rootClassName="tree"
        treeData={treeNodes.value}
        expandedKeys={expandedKeys.value}
        switcherIcon={(node) => (
          <div className="switcher">
            {node.expanded ? (
              <Up className="icon" />
            ) : (
              <Down className="icon" />
            )}
          </div>
        )}
        onClick={handleNodeClick}
        loadData={(e) => loadSubNodes(e)}
        titleRender={(node) => (
          <SvnTreeNode
            busy={statusContext.busy$}
            node={node}
            openned={expandedKeys.value.includes(node.key as string)}
            fetchLogs={fetchLogs}
            fetchRevLogs={props.fetchRevLogs}
          />
        )}
      />
    </Modal>
  );
};
