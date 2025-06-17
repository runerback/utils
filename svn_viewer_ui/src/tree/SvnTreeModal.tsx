import type { ReadonlySignal } from "@preact/signals-react";
import { useSignal, useSignals } from "@preact/signals-react/runtime";
import { Modal, Tree, type TreeDataNode } from "antd";
import DOM from "../assets/DOM.svg?react";
import Close from "../assets/ChromeClose.svg?react";
import SvnTree from "./SvnTree";
import { useCallback } from "preact/hooks";
import SvnTreeNodeIcon from "./SvnTreeNodeIcon";

const treeNodesLookup: Record<string, SvnTreeNode[]> = {};
const buildTree = (root: string): TreeDataNode[] => {
  const nodes = treeNodesLookup[root];
  if (!nodes || nodes.length === 0) {
    return [];
  }
  return nodes.map((node) => {
    const next = root === "/" ? root + node.name : [root, node.name].join("/");
    return {
      key: next,
      title: (
        <div>
          <SvnTreeNodeIcon node={node} />
          {node.name}
        </div>
      ),
      children: node.kind === "DIR" ? buildTree(next) : [],
    } as TreeDataNode;
  });
};

export default (props: {
  busy: ReadonlySignal<boolean>;
  open: ReadonlySignal<boolean>;
  onClose: () => void;
  onFetched: () => void;
}) => {
  useSignals();
  const treeNodes = useSignal(Array<TreeDataNode>());
  const onTreeChange = useCallback(
    (root: string | undefined, nodes: SvnTreeNode[]) => {
      treeNodesLookup[root ?? "/"] = nodes;
      treeNodes.value = buildTree("/");
    },
    []
  );
  return (
    <Modal
      title={
        <div>
          <DOM className="icon" />
          &nbsp;svn tree
        </div>
      }
      width="80vw"
      style={{ maxHeight: "80vh" }}
      closable
      closeIcon={<Close className="icon" onClick={() => props.onClose()} />}
      open={props.open.value}
      cancelButtonProps={{ style: { display: "none" } }}
      okButtonProps={{ style: { display: "none" } }}
    >
      <SvnTree {...props} onChange={onTreeChange} />
      <Tree treeData={treeNodes.value} />
    </Modal>
  );
};
