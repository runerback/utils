/// <reference path="../src/index.d.ts" />

import { describe, it, expect } from "@jest/globals";

import parser from "../src/svnparser";
const { parse_diff } = parser;
const settings = {
  svn_root: "",
  svn_repo: "",
  svn_rev: "",
  dark_theme: false,
};

describe("should parse svn status", () => {
  it("simple", () => {
    const raw =
      "Index: /path/to/file.abc" +
      "\n===================================================================\n--- /path/to/file.abc" +
      "\t(revision 123)\n+++ /path/to/file.abc\t(working copy)\n@@ -2,7 +2,6 @@" +
      "\nline 1\nline 2\n \n-\nline 3\nline 4\nline 5\n";
    const parsed = parse_diff(raw, settings);
    expect(parsed).not.toBeFalsy();
    expect(parsed.length).toBe(1);
    expect(parsed[0].index).toBe("path/to/file.abc");
    expect(parsed[0].versions).not.toBeFalsy();
    expect(parsed[0].versions.length).toBe(2);
    expect(parsed[0].versions[0].version).toBe("revision 123");
    expect(parsed[0].versions[1].version).toBe("working copy");
    expect(parsed[0].sections).not.toBeFalsy();
    expect(parsed[0].sections.length).toBe(1);
    const section = parsed[0].sections[0];
    expect(section.hunk).toEqual({ a: "-", b: 2, c: 7, d: "+", e: 2, f: 6 });
    expect(section.changes).not.toBeFalsy();
    expect(section.changes.length).toBe(7);
  });
});
