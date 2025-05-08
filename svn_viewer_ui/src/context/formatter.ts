export const formatHunk = (hunk: ChunkSectionHunk) =>
  `@@ ${hunk.a}${hunk.b},${hunk.c} ${hunk.d}${hunk.e},${hunk.f} @@`;

export const withLineNumber = (section: ChunkSection) => {
  return [...section.changes];
};
