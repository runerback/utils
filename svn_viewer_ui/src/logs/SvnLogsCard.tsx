import {
  useComputed,
  useSignal,
  type ReadonlySignal,
} from "@preact/signals-react";
import { Button, Card, Checkbox, List, type CheckboxChangeEvent } from "antd";
import type { Moment } from "moment";
import moment from "moment";
import { useCallback } from "preact/hooks";
import type { SvnLogsDiffCardProps } from "./SvnLogsDiffCard";
import SvnLogsDiffCard from "./SvnLogsDiffCard";
import { useSignals } from "@preact/signals-react/runtime";
import SvnLogTitle from "./SvnLogTitle";

export default function (props: {
  log: SvnLogs;
  settings: ReadonlySignal<Settings | undefined>;
}) {
  useSignals();
  const checkedRevisions = useSignal(Array<string>());
  const comparedRevisions = useSignal<
    Record<string, { revisionR: string; timestamp: Moment }[]>
  >({});
  const canCheckMore = useComputed(() => checkedRevisions.value.length < 2);
  const comparing = useSignal(false);
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
  const canCompare = useComputed(() => {
    if (comparing.value) {
      return false;
    }
    if (checkedRevisions.value.length > 1) {
      const [revisionL, revisionR] = checkedRevisions.peek();
      const comparedRevisionsSnapshot = comparedRevisions.peek();
      const comparedRs = comparedRevisionsSnapshot[revisionL];
      if (
        comparedRs &&
        comparedRs.map((it) => it.revisionR).includes(revisionR)
      ) {
        return false;
      }
      const compareLs = comparedRevisionsSnapshot[revisionR];
      if (
        compareLs &&
        compareLs.map((it) => it.revisionR).includes(revisionL)
      ) {
        return false;
      }
      return true;
    }
  });
  const compare = useCallback(() => {
    if (!comparing.value) {
      const [revisionL, revisionR] = checkedRevisions.value;
      const comparedRevisionsSnapshot = comparedRevisions.value;
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
    }
  }, []);
  const swapRevisions = useCallback((left: string, right: string) => {
    const snapshot = { ...comparedRevisions.value };
    const oldLeftEntries = snapshot[left];
    if (!oldLeftEntries) {
      return;
    }

    const filteredOldLeft = oldLeftEntries.filter(
      (it) => it.revisionR !== right
    );

    if (snapshot[right]?.some((it) => it.revisionR === left)) {
      if (filteredOldLeft.length > 0) {
        snapshot[left] = filteredOldLeft;
      } else {
        delete snapshot[left];
      }
      comparedRevisions.value = snapshot;
      return;
    }

    if (filteredOldLeft.length > 0) {
      snapshot[left] = filteredOldLeft;
    } else {
      delete snapshot[left];
    }

    snapshot[right] = [
      ...(snapshot[right] ?? []),
      { revisionR: left, timestamp: moment() },
    ];

    comparedRevisions.value = snapshot;
  }, []);
  const logsDiffCardSource = useComputed(() =>
    Object.entries(comparedRevisions.value)
      .flatMap(([revisionL, revisionRs]) =>
        revisionRs.map(
          ({ revisionR, timestamp }) =>
            [timestamp, revisionL, revisionR] as [Moment, string, string]
        )
      )
      .sort(
        ([timestampL], [timestampR]) =>
          timestampR.valueOf() - timestampL.valueOf()
      )
      .map(([, left, right]) => ({ left, right }))
  );
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
  if (!props.log.status?.source) {
    return <div>{"<no-source>"}</div>;
  }
  return (
    <Card title={props.log.status.source} className="svnlogscard">
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
                !props.log.logs ||
                props.log.logs.length < 2 ||
                (!checkedRevisions.value.includes(item.revision) &&
                  !canCheckMore.value)
              }
              value={item.revision}
              checked={checkedRevisions.value.includes(item.revision)}
              onChange={(e) => handleRevisionSelect(e)}
            >
              <SvnLogTitle log={item} />
            </Checkbox>
          </List.Item>
        )}
      />
      {!canCheckMore.value && (
        <div className="comparer">
          <Button
            type="primary"
            loading={comparing.value}
            disabled={!canCompare.value}
            onClick={compare}
          >
            Compare
          </Button>
        </div>
      )}
      {logsDiffCardSource.value.map(({ left, right }) => (
        <SvnLogsDiffCard
          key={`${left}-${right}`}
          status={props.log.status!}
          settings={props.settings}
          revisions={{ left, right }}
          compareStarted={(e) => compareStatusChanged(e, "STARTED")}
          compareFinished={(e) => compareStatusChanged(e, "FINISHED")}
          onSwap={() => swapRevisions(left, right)}
        />
      ))}
    </Card>
  );
}
