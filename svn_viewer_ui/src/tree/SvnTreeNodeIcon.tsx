import Folder from "../assets/FolderHorizontal.svg?react";
import File from "../assets/Page.svg?react";

export default (props: { node: SvnTreeNode; spinning?: boolean }) => {
  if (props.node.kind === "DIR") {
    return <Folder className={!!props.spinning ? "icon spin" : "icon"} />;
  }
  return <File className={!!props.spinning ? "icon spin" : "icon"} />;
};
