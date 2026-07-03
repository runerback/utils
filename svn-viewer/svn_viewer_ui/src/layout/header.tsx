export default function (props: {
  children?: preact.ComponentChildren | undefined;
}) {
  return <div class="header">{props.children}</div>;
}
