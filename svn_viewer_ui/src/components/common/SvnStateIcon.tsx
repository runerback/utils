import Modified from "../../assets/Modified.svg?react";
import Deleted from "../../assets/Deleted.svg?react";
import Added from "../../assets/Added.svg?react";
import Replaced from "../../assets/Replaced.svg?react";
import Conflicted from "../../assets/Conflicted.svg?react";
import Unversioned from "../../assets/Unversioned.svg?react";
import Missing from "../../assets/Missing.svg?react";

export default (props: { state: string; className?: string }) => {
  switch (props.state) {
    case "M":
      return <Modified className={props.className ?? "icon p5"} />;
    case "D":
      return <Deleted className={props.className ?? "icon p5"} />;
    case "A":
      return <Added className={props.className ?? "icon p5"} />;
    case "R":
      return <Replaced className={props.className ?? "icon p5"} />;
    case "C":
      return <Conflicted className={props.className ?? "icon p5"} />;
    case "?":
      return <Unversioned className={props.className ?? "icon p5"} />;
    case "!":
      return <Missing className={props.className ?? "icon p5"} />;
    default:
      return props.state;
  }
};
