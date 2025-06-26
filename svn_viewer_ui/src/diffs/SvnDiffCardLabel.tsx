import { useComputed } from "@preact/signals-react";
import SvnStateIcon from "../components/common/SvnStateIcon";
import "./SvnDiffCardLabel.css";

const Hightlight = (props: { source: string; match: string }) => {
  const matchIdx = useComputed(() => props.source.indexOf(props.match));
  const parts = useComputed(() => {
    if (matchIdx.value >= 0) {
      return [
        {
          text: props.source.slice(0, matchIdx.value),
        },
        {
          text: props.match,
          className: "matched",
        },
        {
          text: props.source.slice(matchIdx.value + props.match.length),
        },
      ].filter((it) => Boolean(it.text));
    }
    return [{ text: props.source }];
  });
  return (
    <>
      {parts.value?.map((p) => (
        <span className={p.className}>{p.text}</span>
      ))}
    </>
  );
};

export default (props: { status: SvnStatusItem; hightlight?: string }) => {
  const stateTip = useComputed(() => {
    switch (props.status.state) {
      case "M":
        return "Modified";
      case "D":
        return "Deletion";
      case "A":
        return "Adding";
      case "R":
        return "Replaced";
      case "C":
        return "Conflicted";
      case "?":
        return "Not under version control";
      case "!":
        return "Missing";
      default:
        return props.status.state;
    }
  });
  const rev = useComputed(() => {
    return props.status as SvnRevStatusItem;
  });
  return (
    <div className="changes">
      <div className="state">
        <span className="tooltip">{stateTip.value}</span>
        <SvnStateIcon state={props.status.state} />
      </div>
      <div className="source">
        {!!props.hightlight && !!rev.value?.highlight ? (
          <Hightlight source={props.status.source} match={props.hightlight} />
        ) : (
          props.status.source
        )}
        {rev.value && rev.value.from && (
          <span>
            &nbsp;(from:&nbsp;<b>{rev.value.from}</b>
            {rev.value.rev && (
              <span>
                &nbsp;:&nbsp;<b>{rev.value.rev}</b>
              </span>
            )}
            )
          </span>
        )}
      </div>
    </div>
  );
};
