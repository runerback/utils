import {
  useSignal,
  useSignalEffect,
  type ReadonlySignal,
} from "@preact/signals-react";
import { Button, Collapse, Space, Spin } from "antd";
import { useCallback, useContext, useMemo } from "preact/hooks";
import { SvnLogDiffsContext } from "../context/svnLogDiffsContext";
import { filter } from "rxjs";
import SvnDiffCardContent from "../diffs/SvnDiffCardContent";
import { useSignals } from "@preact/signals-react/runtime";
import Refresh from "../assets/Refresh.svg?react";

export type SvnLogsDiffCardProps = {
  status: SvnStatusItem;
  revisions: {
    left: string;
    right: string;
  };
};

export default function (
  props: SvnLogsDiffCardProps & {
    settings: ReadonlySignal<Settings | undefined>;
    compareStarted: (e: SvnLogsDiffCardProps) => void;
    compareFinished: (e: SvnLogsDiffCardProps) => void;
  }
) {
  useSignals();
  const svnLogDiffsContext = useContext(SvnLogDiffsContext);
  const actived = useSignal(true);
  const fetched = useSignal(false);
  const fetching = useSignal(false);
  const fetchId = useSignal("");
  const diffs = useSignal<Chunk1>();
  const unversioned = useSignal(Array<string>());
  const key = useMemo(
    () => [props.revisions.left, props.revisions.right].join("-"),
    []
  );
  useSignalEffect(() => {
    svnLogDiffsContext.stream$
      .pipe(
        filter(
          (it) =>
            !!it &&
            !!it.id &&
            it.job === "FETCH_LOG_DIFFS" &&
            it.id === fetchId.value
        )
      )
      .subscribe((e) => {
        if (!!e.chunks && e.chunks.length > 0) {
          diffs.value = e.chunks[0];
        }
        if (!!e.finished) {
          fetching.value = false;
          fetched.value = true;
          props.compareFinished(props);
        }
      });
  });
  useSignalEffect(() => {
    if (actived.value && !fetched.value && !fetching.value) {
      fetch();
    }
  });
  const fetch = useCallback(() => {
    fetching.value = true;
    props.compareStarted(props);
    svnLogDiffsContext
      .provide(props.status, {
        n: parseInt(props.revisions.left) ?? 0,
        m: parseInt(props.revisions.right) ?? undefined,
      })
      .then((id) => {
        if (!!id) {
          fetchId.value = id;
        } else {
          fetchId.value = "";
        }
      });
  }, []);
  return (
    <Spin spinning={fetching.value}>
      <Collapse
        bordered
        activeKey={actived.value ? [key] : []}
        onChange={(e) => (actived.value = e.length > 0)}
        items={[
          {
            key: key,
            label: (
              <div>
                <b>{props.revisions.left}</b>
                &nbsp;-&nbsp;
                <b>{props.revisions.right}</b>
              </div>
            ),
            extra: (
              <Space>
                <Button
                  loading={fetching.value}
                  icon={
                    <Refresh
                      className={fetching.value ? "icon spin" : "icon"}
                    />
                  }
                  title="Reload"
                  onClick={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    fetch();
                  }}
                />
              </Space>
            ),
            children: [
              <SvnDiffCardContent
                diffs={diffs}
                unversioned={unversioned}
                busy={fetching}
                status={props.status}
                settings={props.settings}
              />,
            ],
          },
        ]}
      />
    </Spin>
  );
}
