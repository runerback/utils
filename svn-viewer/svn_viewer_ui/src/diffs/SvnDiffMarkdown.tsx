import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSvnDiff from "../context/rehypeSvnDiff";
import { useSignal, type ReadonlySignal } from "@preact/signals-react";
import { useSignalEffect, useSignals } from "@preact/signals-react/runtime";
import type { Key } from "preact";
import { useRef } from "preact/hooks";

const DEFAULT_LINE_NUMBER_CHAR_WIDTH = 6;
const DEFAULT_LINE_NUMBER_GAP = 16;
const DEFAULT_THEME = {
  href: "../../node_modules/highlight.js/styles/github.min.css",
  addition_bg: "#f0fff4",
  deletion_bg: "#ffeef0",
};
const DARK_THEME = {
  href: "../../node_modules/highlight.js/styles/github-dark.min.css",
  addition_bg: "#063a18",
  deletion_bg: "#66030f",
};

export default function (props: {
  content?: string;
  fkey?: Key;
  maxLine: ReadonlySignal<number>;
  settings: ReadonlySignal<Settings | undefined>;
}) {
  useSignals();
  const diffPanel = useRef<HTMLDivElement>(null);
  const markdownTheme = useSignal(DEFAULT_THEME);
  useSignalEffect(() => {
    const currentSettings = props.settings.value;
    if (!!currentSettings && !!currentSettings.dark_theme) {
      markdownTheme.value = DARK_THEME;
    } else {
      markdownTheme.value = DEFAULT_THEME;
    }
  });
  useSignalEffect(() => {
    if (!diffPanel.current) {
      return;
    }
    const theme = markdownTheme.value;
    diffPanel.current.style.setProperty(
      "--diff-addition-background",
      theme.addition_bg
    );
    diffPanel.current.style.setProperty(
      "--diff-deletion-background",
      theme.deletion_bg
    );
  });
  useSignalEffect(() => {
    if (!diffPanel.current) {
      return;
    }
    const lineWidth =
      props.maxLine.toString().length * DEFAULT_LINE_NUMBER_CHAR_WIDTH;
    diffPanel.current.style.setProperty(
      "--line-number-width",
      `${lineWidth + DEFAULT_LINE_NUMBER_GAP}px`
    );
  });
  return (
    <div className="diff_panel" ref={diffPanel}>
      <link rel="stylesheet" type="text/css" href={markdownTheme.value.href} />
      <Markdown
        key={props.fkey}
        remarkPlugins={[[remarkGfm]]}
        rehypePlugins={[[rehypeSvnDiff]]}
      >
        {props.content}
      </Markdown>
    </div>
  );
}
