import { Collapse, Descriptions } from "antd";
import type { Key } from "preact";

export default function (props: { log: SvnLogs; fkey?: Key }) {
  return (
    <Collapse
      bordered
      key={props.fkey}
      className="svnlogscard"
      defaultActiveKey={props.fkey === "0" ? [1] : []}
      items={[
        {
          key: 1,
          label: (
            <div className="title">
              {props.log.status?.source ?? "<nothing>"}
            </div>
          ),
          children: props.log.logs
            ?.map((it) => (
              <Descriptions column={1}>
                {!!it.message && (
                  <Descriptions.Item label="Message">
                    {it.message}
                  </Descriptions.Item>
                )}
                <Descriptions.Item label="Revision">
                  <b>r</b>
                  {it.revision}
                </Descriptions.Item>
                {!!it.author && (
                  <Descriptions.Item label="Author">
                    {it.author}
                  </Descriptions.Item>
                )}
                {!!it.timestamp && (
                  <Descriptions.Item label="Timestamp">
                    {it.timestamp}
                  </Descriptions.Item>
                )}
                {!!it.changes && it.changes > 0 && (
                  <Descriptions.Item label="Changes">
                    {it.changes}&nbsp;<b>line</b>
                  </Descriptions.Item>
                )}
              </Descriptions>
            ))
            .filter(Boolean),
        },
      ]}
    />
  );
}
