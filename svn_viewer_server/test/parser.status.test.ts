/// <reference path="../src/index.d.ts" />

import { describe, it, expect } from "@jest/globals";

import parser from "../src/svnparser";
const { parse_status } = parser;
const settings = {
  svn_root: "",
  svn_repo: "",
  svn_rev: "",
  dark_theme: false,
};

describe("should parse svn status", () => {
  it("simple", () => {
    const raw = "M       /path/to/file.abc";
    const parsed = parse_status(raw, settings);
    expect(parsed).not.toBeFalsy();
    expect(parsed.length).toBe(1);
    expect(parsed[0].changelist).toBe("no-change-list");
    expect(parsed[0].changes).not.toBeFalsy();
    expect(parsed[0].changes.length).toBe(1);
    expect(parsed[0].changes[0].state).toBe("M");
    expect(parsed[0].changes[0].source).toBe("path/to/file.abc");
  });
});
