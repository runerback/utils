import type { Root, ElementContent, Element, Text } from "hast";
import { common, createLowlight } from "lowlight";
import { visit } from "unist-util-visit";
import { toText } from "hast-util-to-text";

const name = "hljs";
const addition = `${name}-addition`;
const deletion = `${name}-deletion`;

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
                children: [node],
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
                  node,
                ],
              } as Element);
              working_number++;
              return;
            }
            if (classes.includes(deletion)) {
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
                  node,
                ],
              } as Element);
              revision_number++;
              return;
            }
          }
          // no-change line
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
              node,
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
