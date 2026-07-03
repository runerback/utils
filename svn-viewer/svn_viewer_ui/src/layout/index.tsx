import Content from "./content";
import Header from "./header";
import "./layout.css";

export { Header, Content };

export default function (props: {
  children?: preact.ComponentChildren | undefined;
}) {
  return <div class="container">{props.children}</div>;
}
