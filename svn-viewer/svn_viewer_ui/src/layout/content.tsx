export default function (props: {
  children?: preact.ComponentChildren | undefined;
}) {
  return <div class="content">{props.children}</div>;
}
