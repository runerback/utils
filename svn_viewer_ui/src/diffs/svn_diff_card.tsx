import { Button, Collapse, Space, Spin } from "antd";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useCallback, useContext } from "preact/hooks";
import { SvnContext } from "../context/svnContext";
import { filter } from "rxjs";
import type { ReadonlySignal } from "@preact/signals-react";
import network from "../context/network";
import SvnDiffCardLabel from "./svn_diff_card_label";
import SvnDiffCardContent from "./svn_diff_card_content";
import History from "../assets/History.svg?react";
import Refresh from "../assets/Refresh.svg?react";
import OpenFolder from "../assets/OpenFolderHorizontal.svg?react";

export function SvnDiffCard(props: {
  fkey?: Key;
  status: SvnStatusItem;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
  fetchLogs: (status: SvnStatusItem) => void;
}) {
  useSignals();
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const diffId = useSignal("");
  const diffs = useSignal<Chunk1>();
  const unversioned = useSignal(Array<string>());
  const svnContext = useContext(SvnContext);
  useSignalEffect(() => {
    svnContext.stream$
      .pipe(
        filter(
          (it) =>
            !!it && !!it.id && it.job !== "FETCH_LOGS" && it.id === diffId.value
        )
      )
      .subscribe((e) => {
        if (!!e.chunks && e.chunks.length > 0) {
          diffs.value = e.chunks[0];
        } else if (!!e.unversioned && e.unversioned.length > 0) {
          unversioned.value = e.unversioned;
        }
        busy.value = false;
      });
  });
  useSignalEffect(() => {
    if (fetching.value && !fetched.value) {
      fetching.value = false;
      fetched.value = true;
      busy.value = true;
      svnContext.provide(props.status).then((id) => {
        if (!!id) {
          diffId.value = id;
        } else {
          diffId.value = "";
          busy.value = false;
        }
      });
    }
  });
  const fetch = useCallback((key?: string[]) => {
    if (typeof key === "undefined") {
      fetched.value = false;
      fetching.value = true;
      return;
    }
    if (fetched.value) {
      return;
    }
    fetching.value = true;
  }, []);
  const canShowLog = useComputed(() => {
    return props.status.state !== "?";
  });
  return (
    <div className="diffcard">
      <Spin spinning={busy.value}>
        <Collapse
          bordered
          key={props.fkey}
          onChange={fetch}
          items={[
            {
              label: <SvnDiffCardLabel status={props.status} />,
              extra: (
                <Space>
                  {canShowLog.value && (
                    <Button
                      loading={busy.value}
                      icon={
                        <History
                          className={busy.value ? "icon spin" : "icon"}
                        />
                      }
                      title="Show Logs"
                      onClick={(e) => {
                        e.stopPropagation();
                        props.fetchLogs(props.status);
                      }}
                    />
                  )}
                  <Button
                    loading={busy.value}
                    icon={
                      <Refresh className={busy.value ? "icon spin" : "icon"} />
                    }
                    title="Reload"
                    onClick={(e) => {
                      e.stopPropagation();
                      fetch();
                    }}
                  />
                  <Button
                    loading={busy.value}
                    icon={
                      <OpenFolder
                        className={busy.value ? "icon spin" : "icon"}
                      />
                    }
                    title="Open Containing Folder"
                    onClick={(e) => {
                      e.stopPropagation();
                      network.open_in_dir(props.status.source);
                    }}
                  />
                </Space>
              ),
              children: [
                <SvnDiffCardContent
                  diffs={diffs}
                  unversioned={unversioned}
                  busy={busy}
                  {...props}
                />,
              ],
            },
          ]}
        />
      </Spin>
    </div>
  );
}
