import type { ReadonlySignal } from "@preact/signals-react";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import { useMemo } from "preact/hooks";
import SvnDiffMarkdown from "./svn_diff_markdown";
import SvnUnversionedMarkdown from "./svn_unversioned_markdown";
import "./svn_diff_card_content.css";

export default (props: {
  status: SvnStatusItem;
  diffs: ReadonlySignal<Chunk1 | undefined>;
  unversioned: ReadonlySignal<Array<string>>;
  busy: ReadonlySignal<boolean>;
  settings: ReadonlySignal<Settings | undefined>;
}) => {
  useSignals();
  const language = useMemo(() => {
    const lastDotIdx = props.status.source.lastIndexOf(".");
    if (lastDotIdx > 0) {
      return props.status.source.substring(lastDotIdx + 1);
    }
  }, [props.status]);
  const diffContent = useComputed(() => {
    if (!!props.diffs.value) {
      return [
        ...props.diffs.value.sections.flatMap((section, idx) => {
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
    if (!!props.unversioned.value) {
      return props.unversioned.value;
    }
    return [];
  });
  const maxLine = useComputed(() => {
    let max = 0;
    if (!!props.diffs.value) {
      props.diffs.value.sections.forEach((section) => {
        const current =
          Math.max(section.hunk.b, section.hunk.e) + section.changes.length;
        if (current > max) {
          max = current;
        }
      });
    } else if (!!props.unversioned.value) {
      max = props.unversioned.value.length;
    }
    return max;
  });
  const hasContent = useComputed(() => {
    if (!!props.diffs.value) {
      return true;
    }
    if (!!unversionedContent.value && unversionedContent.value.length > 0) {
      return true;
    }
    return false;
  });
  return (
    <div>
      <div className="header">
        <div className="indicator">
          {!props.busy.value &&
            (!hasContent.value ? (
              <div>{!hasContent.value && <i>🈳️ NOTHING HERE 🫥</i>}</div>
            ) : (
              !!props.diffs.value &&
              props.diffs.value.versions.map(({ indicator, version }) => (
                <div>
                  <b>
                    {indicator} {version}
                  </b>
                </div>
              ))
            ))}
        </div>
      </div>
      {!!diffContent.value && (
        <SvnDiffMarkdown
          content={diffContent.value}
          maxLine={maxLine}
          {...props}
        />
      )}
      {!!unversionedContent.value && unversionedContent.value.length > 0 && (
        <SvnUnversionedMarkdown
          lines$={unversionedContent}
          language={language}
          {...props}
        />
      )}
    </div>
  );
};
