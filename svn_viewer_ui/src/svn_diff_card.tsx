import { Collapse, Spin } from "antd";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeHighlight from "rehype-highlight";
import "../node_modules/highlight.js/styles/github.min.css";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useCallback } from "preact/hooks";
import type { Signal } from "@preact/signals-react";

export function SvnDiffCard(props: {
  key?: Key;
  status: SvnStatusItem;
  diffs: Signal<Chunk1 | undefined>;
  provider: (status: SvnStatusItem) => void;
}) {
  useSignals();
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const { diffs } = props;
  useSignalEffect(() => {
    if (!!diffs.value) {
      busy.value = false;
    }
  });
  const content = useComputed(() => {
    if (!diffs.value) {
      return "";
    }
    return [
      `### ${diffs.value.versions
        .map(({ indicator, version }) => `${indicator} ${version}`)
        .join(", ")}`,
      ...diffs.value.sections.flatMap((section) => [
        `* ${section.summary}`,
        "---",
        "```diff",
        ...section.changes,
        "```",
        "---",
      ]),
    ].join("\n");
  });
  useSignalEffect(() => {
    if (fetching.value && !fetched.value) {
      fetching.value = false;
      fetched.value = true;
      busy.value = true;
      props.provider(props.status);
    }
  });
  const fetch = useCallback((key: string[]) => {
    if (!key || key.length === 0) {
      return;
    }
    if (fetched.value) {
      return;
    }
    fetching.value = true;
  }, []);
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
                <div className="changes">
                  <div className="state">{props.status.state}</div>
                  <div className="source">{props.status.source}</div>
                </div>
              ),
              children: [
                <Markdown
                  remarkPlugins={[[remarkGfm]]}
                  rehypePlugins={[[rehypeHighlight]]}
                >
                  {content.value}
                </Markdown>,
              ],
            },
          ]}
        />
      </Spin>
    </div>
  );
}
