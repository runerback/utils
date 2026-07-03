import Folder from "../assets/FolderHorizontal.svg?react";
import FolderOpened from "../assets/OpenFolderHorizontal.svg?react";
import File from "../assets/Page.svg?react";

export default (props: {
  node: SvnTreeNode;
  spinning?: boolean;
  opened?: boolean;
}) => {
  if (props.node.kind === "DIR") {
    return !!props.opened ? (
      <FolderOpened className={!!props.spinning ? "icon spin" : "icon"} />
    ) : (
      <Folder className={!!props.spinning ? "icon spin" : "icon"} />
    );
  }
  return <File className={!!props.spinning ? "icon spin" : "icon"} />;
};
