import type { Root, ElementContent, Element, Text } from "hast";
import { common, createLowlight } from "lowlight";
import { visit } from "unist-util-visit";
import { toText } from "hast-util-to-text";

const name = "hljs";
const addition = `${name}-addition`;
const deletion = `${name}-deletion`;
const whitespaceMarker = "diff-whitespace-marker";
const whitespaceSpace = "diff-whitespace-space";
const whitespaceTab = "diff-whitespace-tab";
const whitespaceTrailing = "diff-whitespace-trailing";
const diffLineMarker = "diff-line-marker";

export default function () {
  const lowlight = createLowlight(common);

  return function (tree: Root) {
    visit(tree, "element", function (node, _, parent) {
      if (
        node.tagName !== "code" ||
        !parent ||
        parent.type !== "element" ||
        parent.tagName !== "pre"
      ) {
        return;
      }

      if (!Array.isArray(node.properties.className)) {
        node.properties.className = [];
      }

      if (!node.properties.className.includes(name)) {
        node.properties.className.unshift(name);
      }

      const text = toText(node, { whitespace: "pre" });

      let result: Root;

      try {
        result = lowlight.highlight("diff", text);
      } catch (error) {
        console.error(error);
        throw error;
      }

      if (result.children.length > 0) {
        const children = [...(result.children as Array<ElementContent>)];
        if (node.tagName === "code" && !!node.data && !!node.data.meta) {
          let hunk: ChunkSectionHunk | undefined;
          try {
            hunk = JSON.parse(node.data.meta);
          } catch {}
          if (hunk) {
            for (let i = children.length - 1; i >= 0; i--) {
              const child = children[i];
              if (child.type === "text") {
                const splited = child.value.split("\n").map(
                  (line) =>
                    ({
                      type: "text",
                      value: line,
                    } as Text)
                );
                children.splice(i, 1, ...splited.slice(i === 0 ? 0 : 1, -1));
              }
            }
            node.children = [
              {
                type: "element",
                tagName: "div",
                properties: {
                  className: ["diff_row"],
                },
                children: [
                  {
                    type: "element",
                    tagName: "span",
                    properties: {},
                    children: [],
                  },
                  {
                    type: "element",
                    tagName: "span",
                    properties: {},
                    children: [],
                  },
                  {
                    type: "element",
                    tagName: "span",
                    properties: {
                      className: ["number"],
                    },
                    children: [
                      {
                        type: "element",
                        tagName: "span",
                        properties: {
                          className: ["hunk"],
                        },
                        children: [
                          {
                            type: "text",
                            value: `@@ ${hunk.a}${hunk.b},${hunk.c} ${hunk.d}${hunk.e},${hunk.f} @@`,
                          },
                        ],
                      },
                    ],
                  },
                ],
              },
              ...appendLineNumbers(children, hunk),
            ];
            return;
          }
        }

        node.children = children;
      }
    });
  };
}

const appendLineNumbers = (
  nodes: ElementContent[],
  hunk?: ChunkSectionHunk
) => {
  if (!!hunk) {
    let revision_number = hunk.b;
    let working_number = hunk.e;
    const lines = Array<ElementContent>();
    nodes.forEach((node) => {
      switch (node.type) {
        case "text":
          const whitespaceLine = visualizeWhitespaceInText(node.value);
          lines.push({
            type: "element",
            tagName: "div",
            properties: {
              className: ["diff_row"],
            },
            children: [
              {
                type: "element",
                tagName: "span",
                properties: {
                  className: ["number"],
                },
                children: [{ type: "text", value: revision_number }],
              },
              {
                type: "element",
                tagName: "span",
                properties: {
                  className: ["number"],
                },
                children: [{ type: "text", value: working_number }],
              },
              {
                type: "element",
                tagName: "span",
                properties: {},
                children: whitespaceLine,
              },
            ],
          } as Element);
          revision_number++;
          working_number++;
          break;
        case "element":
          const classes = node.properties?.["className"];
          if (Array.isArray(classes)) {
            if (classes.includes(addition)) {
              const visualizedNode = visualizeWhitespaceInNode(
                splitLeadingDiffMarker(node, "+")
              );
              lines.push({
                type: "element",
                tagName: "div",
                properties: {
                  className: ["diff_row"],
                },
                children: [
                   {
                     type: "element",
                     tagName: "span",
                     properties: {
                       className: ["number", "addition"],
                    },
                    children: [{ type: "text", value: "" }],
                  },
                  {
                    type: "element",
                    tagName: "span",
                    properties: {
                      className: ["number", "addition"],
                    },
                    children: [{ type: "text", value: working_number }],
                   },
                   visualizedNode,
                 ],
               } as Element);
               working_number++;
               return;
            }
            if (classes.includes(deletion)) {
              const visualizedNode = visualizeWhitespaceInNode(
                splitLeadingDiffMarker(node, "-")
              );
              lines.push({
                type: "element",
                tagName: "div",
                properties: {
                  className: ["diff_row"],
                },
                children: [
                   {
                     type: "element",
                     tagName: "span",
                     properties: {
                       className: ["number", "deletion"],
                    },
                    children: [{ type: "text", value: revision_number }],
                  },
                  {
                    type: "element",
                    tagName: "span",
                    properties: {
                      className: ["number", "deletion"],
                    },
                    children: [{ type: "text", value: "" }],
                   },
                   visualizedNode,
                 ],
               } as Element);
               revision_number++;
               return;
            }
          }
          // no-change line
          const visualizedNode = visualizeWhitespaceInNode(node);
          lines.push({
            type: "element",
            tagName: "div",
            properties: {
              className: ["diff_row"],
            },
            children: [
              {
                type: "element",
                tagName: "span",
                properties: {
                  className: ["number"],
                },
                children: [{ type: "text", value: revision_number }],
              },
              {
                type: "element",
                tagName: "span",
                properties: {
                  className: ["number"],
                },
               children: [{ type: "text", value: working_number }],
              },
              visualizedNode,
            ],
          } as Element);
          revision_number++;
          working_number++;
          break;
        default:
          break;
      }
    });
    return lines;
  }
  return nodes;
};

const splitLeadingDiffMarker = (node: Element, marker: string): Element => {
  const state = { done: false };
  return {
    ...node,
    children: splitLeadingDiffMarkerInChildren(
      node.children as ElementContent[],
      marker,
      state
    ),
  };
};

const splitLeadingDiffMarkerInChildren = (
  children: ElementContent[],
  marker: string,
  state: { done: boolean }
) => {
  if (state.done) {
    return children;
  }
  const transformed = Array<ElementContent>();
  children.forEach((child) => {
    if (state.done) {
      transformed.push(child);
      return;
    }
    switch (child.type) {
      case "text":
        if (child.value.startsWith(marker)) {
          transformed.push({
            type: "element",
            tagName: "span",
            properties: { className: [diffLineMarker] },
            children: [{ type: "text", value: marker }],
          } as Element);
          const rest = child.value.slice(1);
          if (rest.length > 0) {
            transformed.push({
              type: "text",
              value: rest,
            } as Text);
          }
          state.done = true;
          return;
        }
        transformed.push(child);
        return;
      case "element":
        transformed.push({
          ...child,
          children: splitLeadingDiffMarkerInChildren(
            child.children as ElementContent[],
            marker,
            state
          ),
        });
        return;
      default:
        transformed.push(child);
        return;
    }
  });
  return transformed;
};

const visualizeWhitespaceInNode = (node: Element): Element => {
  return {
    ...node,
    children: visualizeWhitespaceInChildren(node.children as ElementContent[]),
  };
};

const visualizeWhitespaceInChildren = (children: ElementContent[]) => {
  const transformed = Array<ElementContent>();
  children.forEach((child) => {
    switch (child.type) {
      case "text":
        transformed.push(...visualizeWhitespaceInText(child.value));
        break;
      case "element":
        transformed.push(visualizeWhitespaceInNode(child));
        break;
      default:
        transformed.push(child);
    }
  });
  return transformed;
};

const visualizeWhitespaceInText = (line: string) => {
  const trailing = /[ \t]+$/.exec(line);
  const trailingFrom = trailing ? line.length - trailing[0].length : line.length;
  const nodes = Array<ElementContent>();
  let textBuffer = "";
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch !== " " && ch !== "\t") {
      textBuffer += ch;
      continue;
    }
    if (textBuffer.length > 0) {
      nodes.push({
        type: "text",
        value: textBuffer,
      } as Text);
      textBuffer = "";
    }
    const className = [
      whitespaceMarker,
      ch === " " ? whitespaceSpace : whitespaceTab,
      ...(i >= trailingFrom ? [whitespaceTrailing] : []),
    ];
    nodes.push({
      type: "element",
      tagName: "span",
      properties: { className },
      children: [
        {
          type: "text",
          value: ch === " " ? "·" : "→",
        },
      ],
    } as Element);
  }
  if (textBuffer.length > 0) {
    nodes.push({
      type: "text",
      value: textBuffer,
    } as Text);
  }
  if (nodes.length === 0) {
    nodes.push({
      type: "text",
      value: line,
    } as Text);
  }
  return nodes;
};
