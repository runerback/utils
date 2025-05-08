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
import { useCallback, useContext, useRef } from "preact/hooks";
import { SvnDiffProviderContext } from "./svnDiffProviderContext";
import { filter } from "rxjs";

const formatHunk = (hunk: ChunkSectionHunk) =>
  `@@ ${hunk.a}${hunk.b},${hunk.c} ${hunk.d}${hunk.e},${hunk.f} @@`;

export function SvnDiffCard(props: {
  key?: Key;
  status: SvnStatusItem;
  observe: (target: HTMLDivElement) => void;
}) {
  useSignals();
  const headerRef = useRef<HTMLDivElement>(null);
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const diffId = useSignal("");
  const diffs = useSignal<Chunk1>();
  const svnDiffProviderContext = useContext(SvnDiffProviderContext);
  useSignalEffect(() => {
    svnDiffProviderContext.stream$
      .pipe(filter((it) => it.id === diffId.value))
      .subscribe((e) => {
        diffs.value = e.chunks?.[0];
        busy.value = false;
      });
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
        `* ${formatHunk(section.hunk)}`,
        "---",
        "```diff",
        ...section.changes, // TODO: line numbers
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
      svnDiffProviderContext
        .provide(props.status)
        .then((id) => (diffId.value = id ?? ""));
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
  useSignalEffect(() => {
    if (!!headerRef.current) {
      props.observe(headerRef.current);
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
