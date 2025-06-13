import { useComputed, useSignal, useSignalEffect } from "@preact/signals-react";
import { Button, Card, Checkbox, List, type CheckboxChangeEvent } from "antd";
import type { Moment } from "moment";
import moment from "moment";
import { useCallback, useMemo } from "preact/hooks";
import type { SvnLogsDiffCardProps } from "./SvnLogsDiffCard";
import SvnLogsDiffCard from "./SvnLogsDiffCard";
import { useSignals } from "@preact/signals-react/runtime";

export default function (props: { log: SvnLogs }) {
  useSignals();
  const source = useMemo(() => {
    return props.log.status?.source;
  }, []);
  const checkedRevisions = useSignal(Array<string>());
  const comparedRevisions = useSignal<
    Record<string, { revisionR: string; timestamp: Moment }[]>
  >({});
  useSignalEffect(() =>
    console.log({ comparedRevisions: comparedRevisions.value })
  );
  const canCheckMore = useComputed(() => checkedRevisions.value.length < 2);
  const comparing = useSignal(false);
  const logsDiffCardSource = useSignal(
    Array<{
      left: string;
      right: string;
    }>()
  );
  const handleRevisionSelect = useCallback((e: CheckboxChangeEvent) => {
    if (e.target.checked) {
      checkedRevisions.value = [...checkedRevisions.value, e.target.value];
    } else {
      const snapshot = checkedRevisions.value;
      const idx = snapshot.indexOf(e.target.value);
      if (idx >= 0) {
        checkedRevisions.value = [
          ...snapshot.slice(0, idx),
          ...snapshot.slice(idx + 1),
        ];
      }
    }
  }, []);
  const compare = useCallback(() => {
    if (!comparing.value) {
      const [revisionL, revisionR] = checkedRevisions.value;
      const comparedRevisionsSnapshot = comparedRevisions.peek();
      const comparedRs = comparedRevisionsSnapshot[revisionL];
      if (
        comparedRs &&
        comparedRs.map((it) => it.revisionR).includes(revisionR)
      ) {
        // TODO: refresh exists
      } else {
        comparedRevisions.value = {
          ...comparedRevisionsSnapshot,
          [revisionL]: [
            ...(comparedRs ?? []),
            { revisionR, timestamp: moment() }, // sort by timestamp desc
          ],
        };
      }
      logsDiffCardSource.value = Object.entries(comparedRevisions.value)
        .flatMap(([revisionL, revisionRs]) =>
          revisionRs.map(
            ({ revisionR, timestamp }) =>
              [timestamp, revisionL, revisionR] as [Moment, string, string]
          )
        )
        .sort(
          ([timestampL], [timestampR]) =>
            timestampL.valueOf() - timestampR.valueOf()
        )
        .map(([, left, right]) => ({ left, right }));
    }
  }, []);
  const compareStatusChanged = useCallback(
    (e: SvnLogsDiffCardProps, status: "STARTED" | "FINISHED") => {
      switch (status) {
        case "STARTED":
          comparing.value = true;
          console.log("comparing started: ", e);
          break;
        case "FINISHED":
          comparing.value = false;
          console.log("comparing finished: ", e);
          break;
        default:
          break;
      }
    },
    []
  );
  if (!source) {
    return <div>{"<no-source>"}</div>;
  }
  return (
    <Card title={source} className="svnlogscard">
      <List
        loading={comparing.value}
        className="logs"
        itemLayout="horizontal"
        dataSource={props.log.logs}
        renderItem={(item, index) => (
          <List.Item key={index}>
            <Checkbox
              className="line"
              disabled={
                !checkedRevisions.value.includes(item.revision) &&
                !canCheckMore.value
              }
              value={item.revision}
              checked={checkedRevisions.value.includes(item.revision)}
              onChange={(e) => handleRevisionSelect(e)}
            >
              <div className="title">
                <span className="revision">{item.revision}</span>
                <span className="message">
                  {item.message ?? "<no-message>"}
                </span>
                <span className="timestamp">{item.timestamp}</span>
                <span className="author">{item.author}</span>
              </div>
            </Checkbox>
          </List.Item>
        )}
      />
      {!canCheckMore.value && (
        <div className="comparer">
          <Button type="primary" loading={comparing.value} onClick={compare}>
            Compare
          </Button>
        </div>
      )}
      {logsDiffCardSource.value.map(({ left, right }) => (
        <SvnLogsDiffCard
          source={source}
          revisions={{ left, right }}
          compareStarted={(e) => compareStatusChanged(e, "STARTED")}
          compareFinished={(e) => compareStatusChanged(e, "FINISHED")}
        />
      ))}
    </Card>
  );
}
