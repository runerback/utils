import type { ReadonlySignal } from "@preact/signals-react";
import { useComputed, useSignals } from "@preact/signals-react/runtime";
import { useMemo } from "preact/hooks";
import "./SvnDiffCardContent.css";
import { lazy, Suspense } from "preact/compat";
import { Skeleton } from "antd";

const SvnDiffMarkdown = lazy(() => import("./SvnDiffMarkdown"));
const SvnRawMarkdown = lazy(() => import("./SvnRawMarkdown"));

export default (props: {
  status: SvnStatusItem;
  diffs: ReadonlySignal<Chunk1 | undefined>;
  unversioned: ReadonlySignal<Array<string>>;
  missing?: ReadonlySignal<Array<string>>;
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
  const missingContent =
    !!props.missing &&
    useComputed(() => {
      if (!!props.missing!.value) {
        return [
          "```diff",
          ...props.missing!.value.map((line) => `-${line}`),
          "```",
        ];
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
    } else if (!!props.missing && !!props.missing.value) {
      max = props.missing.value.length;
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
    if (
      !!missingContent &&
      !!missingContent.value &&
      missingContent.value.length > 0
    ) {
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
        <Suspense fallback={<Skeleton loading />}>
          <SvnDiffMarkdown
            content={diffContent.value}
            maxLine={maxLine}
            {...props}
          />
        </Suspense>
      )}
      {!!unversionedContent.value && unversionedContent.value.length > 0 && (
        <Suspense fallback={<Skeleton loading />}>
          <SvnRawMarkdown
            lines$={unversionedContent}
            language={language}
            {...props}
          />
        </Suspense>
      )}
      {!!missingContent &&
        !!missingContent.value &&
        missingContent.value.length > 0 && (
          <Suspense fallback={<Skeleton loading />}>
            <SvnDiffMarkdown
              content={missingContent.value.join("\n")}
              maxLine={maxLine}
              {...props}
            />
          </Suspense>
        )}
    </div>
  );
};
