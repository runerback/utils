import { Collapse, Spin } from "antd";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  useComputed,
  useSignal,
  useSignalEffect,
  useSignals,
} from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useCallback, useContext, useRef } from "preact/hooks";
import { SvnDiffProviderContext } from "../context/svnDiffProviderContext";
import { filter } from "rxjs";
import type { ReadonlySignal } from "@preact/signals-react";
import rehypeDiffLineNumber from "./rehypeDiffLineNumber";

const DEFAULT_THEME = "../../node_modules/highlight.js/styles/github.min.css";
const DARK_THEME = "../../node_modules/highlight.js/styles/github-dark.min.css";

export function SvnDiffCard(props: {
  key?: Key;
  status: SvnStatusItem;
  settings: ReadonlySignal<Settings | undefined>;
  observe: (target: HTMLElement) => void;
  unobserve: (target: HTMLElement) => void;
}) {
  useSignals();
  const markdownTheme = useSignal(DEFAULT_THEME);
  const headerRef = useRef<HTMLDivElement>(null);
  const fetching = useSignal(false);
  const fetched = useSignal(false);
  const busy = useSignal(false);
  const diffId = useSignal("");
  const diffs = useSignal<Chunk1>();
  const unversioned = useSignal(Array<string>());
  const svnDiffProviderContext = useContext(SvnDiffProviderContext);
  useSignalEffect(() => {
    svnDiffProviderContext.stream$
      .pipe(filter((it) => it.id === diffId.value))
      .subscribe((e) => {
        if (!!e.chunks && e.chunks.length > 0) {
          diffs.value = e.chunks[0];
        } else if (!!e.unversioned && e.unversioned.length > 0) {
          unversioned.value = e.unversioned;
        }
        busy.value = false;
      });
  });
  const content = useComputed(() => {
    if (!!diffs.value) {
      return [
        ...diffs.value.sections.flatMap((section) => {
          return [
            `\`\`\`diff ${JSON.stringify(section.hunk)}`,
            ...section.changes,
            "```",
          ];
        }),
      ].join("\n");
    }
    if (!!unversioned.value) {
      let codeType = "";
      const lastDotIdx = props.status.source.lastIndexOf(".");
      if (lastDotIdx > 0) {
        codeType = props.status.source.substring(lastDotIdx + 1);
      }
      return [`\`\`\`${codeType}`, ...unversioned.value, "```"].join("\n");
    }
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
  useSignalEffect(() => {
    const currentSettings = props.settings.value;
    if (!!currentSettings && !!currentSettings.dark_theme) {
      markdownTheme.value = DARK_THEME;
    } else {
      markdownTheme.value = DEFAULT_THEME;
    }
  });
  const fetch = useCallback((key: string[]) => {
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
  return (
    <div className="diffcard">
      <link rel="stylesheet" type="text/css" href={markdownTheme.value} />
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
                !!diffs.value &&
                  diffs.value.versions.map(({ indicator, version }) => (
                    <div>
                      <b>
                        {indicator} {version}
                      </b>
                    </div>
                  )),
                <Markdown
                  remarkPlugins={[[remarkGfm]]}
                  rehypePlugins={[[rehypeDiffLineNumber]]}
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
