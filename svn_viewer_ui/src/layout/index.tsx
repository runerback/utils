import "./layout.css";

export default function (props: {
  children?: preact.ComponentChildren | undefined;
}) {
  return <div class="container">{props.children}</div>;
}
