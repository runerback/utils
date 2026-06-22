import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import { Modal, Skeleton, Tree } from "antd";
import DOM from "../assets/DOM.svg?react";
import { useCallback, useContext, useEffect } from "preact/hooks";
import "./tree.css";
import type { EventDataNode, FieldDataNode, Key } from "rc-tree/lib/interface";
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

export type TreeDataNode = FieldDataNode<{
  key: Key;
  data: SvnTreeNode;
  title?: React.ReactNode;
}>;

const buildTree = (
  root: string,
  map: Map<string, SvnTreeNode[]>
): TreeDataNode[] => {
  const nodes = map.get(root);
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
        children: node.kind === "DIR" ? buildTree(next, map) : [],
      } as TreeDataNode;
    });
};

const TREE_HEIGHT_RATIO = 0.65;

export default (props: { fetchRevLogs: (dir: string) => void }) => {
  useSignals();
  const statusContext = useContext(StatusContext);
  const svnTreeContext = useContext(SvnTreeContext);
  const signalPromiseContext = useContext(SignalPromiseContext);
  const keyboard = useContext(KeyboardContext);

  const treeNodesMap = useSignal<Map<string, SvnTreeNode[]>>(new Map());
  const treeNodes = useComputed(() => buildTree("/", treeNodesMap.value));
  const loadingRoot = useSignal("/");
  const expandedKeys = useSignal<Array<string>>([]);
  const loadedKeys = useSignal<Set<string>>(new Set());
  const fetching = useSignal(false);
  const fetchId = useSignal("");
  const treeHeight = useSignal(window.innerHeight * TREE_HEIGHT_RATIO);

  useEffect(() => {
    const update = () => {
      treeHeight.value = window.innerHeight * TREE_HEIGHT_RATIO;
    };
    window.addEventListener("resize", update);
    return () => window.removeEventListener("resize", update);
  }, []);

  useSignalEffect(() => {
    if (svnTreeContext.show$.value && treeNodesMap.value.size === 0) {
      loadingRoot.value = "/";
      fetching.value = true;
    }
  });

  useSignalEffect(() => {
    if (!fetching.value) {
      return;
    }
    svnTreeContext.provide(loadingRoot.value).then((id) => {
      fetchId.value = id ?? "";
    });
  });

  useSignalEffect(() => {
    svnTreeContext.stream$
      .pipe(filter((it) => !!it && !!it.id && it.job === "FETCH_TREE"))
      .subscribe((e) => {
        if (!svnTreeContext.show$.value) {
          return;
        }
        if (e.id !== fetchId.value) {
          return;
        }
        if (!!e.nodes && e.nodes.length > 0) {
          const next = new Map(treeNodesMap.value);
          next.set(loadingRoot.value, e.nodes);
          treeNodesMap.value = next;
        }
        if (!!e.finished) {
          const nextLoaded = new Set(loadedKeys.value);
          nextLoaded.add(loadingRoot.value);
          loadedKeys.value = nextLoaded;
          fetching.value = false;
        }
      });
  });

  const handleNodeClick = useCallback(
    (
      _: React.MouseEvent<HTMLSpanElement>,
      node: EventDataNode<TreeDataNode>
    ) => {
      if (node.data.kind !== "DIR") {
        return;
      }
      const key = node.key as string;
      const current = new Set(expandedKeys.value);
      if (node.expanded) {
        current.delete(key);
      } else {
        current.add(key);
      }
      expandedKeys.value = Array.from(current);
    },
    []
  );

  const handleExpand = useCallback((keys: Key[]) => {
    expandedKeys.value = keys as string[];
  }, []);

  const loadSubNodes = useCallback(
    async (e: EventDataNode<TreeDataNode>) => {
      const key = e.key as string;
      if (loadedKeys.value.has(key) || fetching.value) {
        return;
      }
      loadingRoot.value = key;
      fetching.value = true;
      try {
        await signalPromiseContext.provide(fetching, (value) => !value);
      } catch (message) {
        console.error(message);
      }
    },
    []
  );

  const close = useCallback(() => {
    svnTreeContext.close();
    treeNodesMap.value = new Map();
    expandedKeys.value = [];
    loadedKeys.value = new Set();
    loadingRoot.value = "/";
    fetching.value = false;
    fetchId.value = "";
  }, []);

  const fetchLogs = useCallback(
    (status: SvnStatusItem) => {
      provideSvnLogs(status, keyboard.ctrl$.value);
    },
    [keyboard.ctrl$.value]
  );

  const isEmpty = treeNodes.value.length === 0;

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
      {isEmpty && <Skeleton loading />}
      <Tree
        rootClassName="tree"
        treeData={treeNodes.value}
        expandedKeys={expandedKeys.value}
        loadedKeys={Array.from(loadedKeys.value)}
        height={treeHeight.value}
        virtual
        itemHeight={32}
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
        onExpand={handleExpand}
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
