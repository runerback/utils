import { Button, Collapse, Space, Spin } from "antd";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useCallback, useContext } from "preact/hooks";
import { SvnDiffContext } from "../context/svnDiffContext";
import { filter } from "rxjs";
import type { ReadonlySignal } from "@preact/signals-react";
import network from "../context/network";
import SvnDiffCardLabel from "./SvnDiffCardLabel";
import SvnDiffCardContent from "./SvnDiffCardContent";
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
  const missing = useSignal(Array<string>());
  const svnContext = useContext(SvnDiffContext);
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
          unversioned.value = [];
          missing.value = [];
        } else if (!!e.unversioned && e.unversioned.length > 0) {
          unversioned.value = e.unversioned;
          diffs.value = undefined;
          missing.value = [];
        } else if (!!e.missing && e.missing.length > 0) {
          missing.value = e.missing;
          diffs.value = undefined;
          unversioned.value = [];
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
    switch (props.status.state) {
      case "?":
      case "A":
      case "D":
        return false;
      default:
        return true;
    }
  });
  const canViewFile = useComputed(() => {
    switch (props.status.state) {
      case "D":
        return false;
      default:
        return true;
    }
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
                        e.preventDefault();
                        e.stopPropagation();
                        props.fetchLogs(props.status);
                      }}
                    />
                  )}
                  {canViewFile.value && (
                    <Button
                      loading={busy.value}
                      icon={
                        <OpenFolder
                          className={busy.value ? "icon spin" : "icon"}
                        />
                      }
                      title="Open Containing Folder"
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        network.open_in_dir(props.status.source);
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
                  missing={missing}
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
