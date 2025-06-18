import { useSignalEffect, type ReadonlySignal } from "@preact/signals-react";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import type { Key } from "preact";
import { useRef, useMemo } from "preact/hooks";

const LineHeight = 20;
const MaxHeight = 500;

export default function (props: {
  lines$: ReadonlySignal<string[]>;
  language?: string;
  fkey?: Key;
  settings: ReadonlySignal<Settings | undefined>;
}) {
  useSignals();
  const container = useRef<HTMLDivElement>(null!);
  const model = useMemo(() => {
    return monaco.editor.createModel("");
  }, []);
  const theme = useComputed(() => {
    const currentSettings = props.settings.value;
    if (!!currentSettings && !!currentSettings.dark_theme) {
      return "vs-dark";
    }
    return "vs";
  });
  const editor = useComputed(() => {
    if (!!container.current) {
      return monaco.editor.create(container.current, {
        model,
        theme: theme.peek(),
        automaticLayout: true,
        lineNumbers: "on",
        readOnly: true,
        domReadOnly: true,
        wordWrap: "on",
        scrollBeyondLastLine: false,
        lineHeight: LineHeight,
      });
    }
  });
  useSignalEffect(() => {
    const lines = props.lines$.value;
    monaco.editor.setModelLanguage(model, props.language ?? "");
    model.setValue(lines.join("\n"));
    if (!!container.current) {
      container.current.style.setProperty(
        "height",
        `${Math.min(MaxHeight, (lines.length + 1) * LineHeight)}px`
      );
    }
  });
  useSignalEffect(() => {
    if (!editor.value) {
      console.log("no editor!!!");
      return;
    }
    editor.value.updateOptions({ theme: theme.value });
  });
  return (
    <div
      key={props.fkey}
      ref={container}
      className="diff_panel"
      onResize={() => editor.value?.layout()}
    ></div>
  );
}
