import "./SvnLogTitle.css";

export default (props: { log: SvnLog }) => {
  return (
    <div className="title">
      <span className="revision">{props.log.revision}</span>
      <span className="message">{props.log.message ?? ""}</span>
      <span className="timestamp">{props.log.timestamp}</span>
      <span className="author">{props.log.author}</span>
    </div>
  );
};
