import { Button, Collapse, Space, Spin } from "antd";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useCallback, useContext, useMemo, useRef } from "preact/hooks";
import { SvnDiffProviderContext } from "../context/svnDiffProviderContext";
import { filter } from "rxjs";
import type { ReadonlySignal } from "@preact/signals-react";
import SvnDiffMarkdown from "./svn_diff_markdown";
import SvnUnversionedMarkdown from "./svn_unversioned_markdown";
import {
  FolderOpenOutlined,
  HistoryOutlined,
  ReloadOutlined,
} from "@ant-design/icons";
import network from "../context/network";

export function SvnDiffCard(props: {
  key?: Key;
  status: SvnStatusItem;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
}) {
  useSignals();
  const headerRef = useRef<HTMLDivElement>(null);
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const diffId = useSignal("");
  const diffs = useSignal<Chunk1>();
  const unversioned = useSignal(Array<string>());
  const language = useMemo(() => {
    const lastDotIdx = props.status.source.lastIndexOf(".");
    if (lastDotIdx > 0) {
      return props.status.source.substring(lastDotIdx + 1);
    }
  }, [props.status]);
  const svnDiffProviderContext = useContext(SvnDiffProviderContext);
  useSignalEffect(() => {
    svnDiffProviderContext.stream$
      .pipe(filter((it) => it.id === diffId.value))
      .subscribe((e) => {
        if (!!e.chunks && e.chunks.length > 0) {
          diffs.value = e.chunks[0];
        } else if (!!e.unversioned && e.unversioned.length > 0) {
          unversioned.value = e.unversioned;
        } else if (!!e.logs && e.logs.length > 0) {
          console.log({ svn_logs: e.logs });
        }
        busy.value = false;
      });
  });
  const diffContent = useComputed(() => {
    if (!!diffs.value) {
      return [
        ...diffs.value.sections.flatMap((section, idx) => {
          return [
            idx > 0 && "---",
            `\`\`\`diff ${JSON.stringify(section.hunk)}`,
            ...section.changes,
            "```",
          ].filter(Boolean);
        }),
      ].join("\n");
    }
  });
  const unversionedContent = useComputed(() => {
    if (!!unversioned.value) {
      return unversioned.value;
    }
    return [];
  });
  const maxLine = useComputed(() => {
    let max = 0;
    if (!!diffs.value) {
      diffs.value.sections.forEach((section) => {
        const current =
          Math.max(section.hunk.b, section.hunk.e) + section.changes.length;
        if (current > max) {
          max = current;
        }
      });
    } else if (!!unversioned.value) {
      max = unversioned.value.length;
    }
    return max;
  });
  useSignalEffect(() => {
    if (fetching.value && !fetched.value) {
      fetching.value = false;
      fetched.value = true;
      busy.value = true;
      svnDiffProviderContext.provide(props.status).then((id) => {
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
    if (!key || key.length === 0) {
      if (!!headerRef.current) {
        props.unobserve(headerRef.current);
      }
      return;
    }
    if (!!headerRef.current) {
      props.observe(headerRef.current);
    }
    if (fetched.value) {
      return;
    }
    fetching.value = true;
  }, []);
  const isEmpty = useComputed(() => {
    if (!!diffs.value) {
      return false;
    }
    if (!!unversionedContent.value && unversionedContent.value.length > 0) {
      return false;
    }
    return true;
  });
  const stateIcon = useComputed(() => {
    switch (props.status.state) {
      case "M":
        return "Ⓜ️";
      case "D":
        return "❌";
      case "A":
        return "➕";
      case "?":
        return "❓";
      case "!":
        return "❗";
      default:
        return props.status.state;
    }
  });
  return (
    <div className="diffcard">
      <Spin spinning={busy.value}>
        <Collapse
          bordered
          key={props.key}
          onChange={fetch}
          items={[
            {
              label: (
                <div className="changes" ref={headerRef}>
                  <div className="state">{stateIcon.value}</div>
                  <div className="source">{props.status.source}</div>
                </div>
              ),
              extra: (
                <Space>
                  <Button
                    loading={busy.value}
                    icon={<ReloadOutlined spin={busy.value} />}
                    title="Reload"
                    onClick={(e) => {
                      e.stopPropagation();
                      fetch();
                    }}
                  />
                  <Button
                    loading={busy.value}
                    icon={<FolderOpenOutlined spin={busy.value} />}
                    title="Open Containing Folder"
                    onClick={(e) => {
                      e.stopPropagation();
                      network.open_in_dir(props.status.source);
                    }}
                  />
                  <Button
                    loading={busy.value}
                    icon={<HistoryOutlined spin={busy.value} />}
                    title="Show Logs"
                    onClick={(e) => {
                      e.stopPropagation();
                      network.fetch_logs(props.status.source);
                    }}
                  />
                </Space>
              ),
              children: [
                <div className="header">
                  <div className="indicator">
                    {isEmpty.value ? (
                      <div>
                        <i>🈳️ NOTHING HERE 🫥</i>
                      </div>
                    ) : (
                      !!diffs.value &&
                      diffs.value.versions.map(({ indicator, version }) => (
                        <div>
                          <b>
                            {indicator} {version}
                          </b>
                        </div>
                      ))
                    )}
                  </div>
                  <div className="operations"></div>
                </div>,
                !isEmpty.value &&
                  (!!diffContent.value ? (
                    <SvnDiffMarkdown
                      content={diffContent.value}
                      maxLine={maxLine}
                      {...props}
                    />
                  ) : (
                    !!unversionedContent.value &&
                    unversionedContent.value.length > 0 && (
                      <SvnUnversionedMarkdown
                        lines$={unversionedContent}
                        language={language}
                        {...props}
                      />
                    )
                  )),
              ],
            },
          ].filter(Boolean)}
        />
      </Spin>
    </div>
  );
}
