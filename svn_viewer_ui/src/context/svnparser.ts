const parseStatus = (rawStatus: string, settings: Settings): SvnStatus[] => {
  if (!rawStatus) {
    return [];
  }
  const status = Array<SvnStatus>();
  const lines = rawStatus.split(/\r|\n/gs).filter(Boolean);
  let changelist = "NO_CHANGE_LIST";
  const changes = Array<SvnStatusItem>();
  const flush = () => {
    status.push({
      changelist,
      changes: [...changes],
    });
    changes.splice(0);
  };
  lines.forEach((line) => {
    const changelistTest = /-{3}\s*Changelist\s*'(?<cl>.+)'\s*\:/gi.exec(line);
    if (!!changelistTest && !!changelistTest.groups) {
      flush();
      changelist = changelistTest.groups["cl"];
      return;
    }
    const statusTest = /(?<state>.)\s*(?<source>.+)/g.exec(line);
    if (!!statusTest && !!statusTest.groups) {
      const status = statusTest.groups["state"].trim();
      if (status.length === 0) {
        return; // ignored by svn
      }
      const source = statusTest.groups["source"]
        .replace(settings.svn_root, "")
        .replace(/\\/g, "/");
      changes.push({
        state: status,
        source: source[0] === "/" ? source.substring(1) : source,
      });
      return;
    }
  });
  flush();
  return status;
};

const parsechunks0 = (rawdiff: string): Chunk0[] => {
  const lines = rawdiff.split(/\r|\n/gs).filter(Boolean);
  const chunks = [];
  let firstChunk = true;
  let chunk: Partial<Chunk0> = {};
  for (let i = 0, j = lines.length; i < j; i++) {
    const line = lines[i];
    if (/(\={60,})/.test(line)) {
      if (!firstChunk) {
        if (!!chunk.sections) {
          chunk.sections.splice(chunk.sections.length - 1, 1);
        }
        chunks.push(chunk);
        chunk = {};
      } else {
        firstChunk = false;
      }
      chunk = {
        index: lines[i - 1],
        sections: [],
      };
    } else {
      chunk.sections?.push(line);
    }
  }
  if (!!chunk.sections) {
    chunks.push(chunk);
  }
  return chunks as Chunk0[];
};

const buildchunk1 = (chunk: Chunk0, settings: Settings): Chunk1 => {
  const index = chunk.index
    .replace("Index: ", "")
    .replace(settings.svn_root, "");
  const chunk1: Chunk1 = {
    index: index[0] === "/" ? index.substring(1) : index,
    versions: [],
    sections: [],
  };
  let section: ChunkSection | undefined;
  chunk.sections.forEach((line) => {
    const versionMatch = /(?<indicator>(-|\+){3}).+\((?<version>.+)\)/g.exec(
      line
    );
    if (!!versionMatch && !!versionMatch.groups) {
      if (!!section) {
        chunk1.sections.push(section);
        section = undefined;
      }
      const indicator = versionMatch.groups["indicator"];
      const version = versionMatch.groups["version"];
      chunk1.versions.push({
        indicator: indicator === "---" ? "- - - " : indicator,
        version,
      });
      return;
    }
    const hunkMatch =
      /@@\s+(?<a>(\+|-))(?<b>\d+),(?<c>\d+)\s+(?<d>(\+|-))(?<e>\d+),(?<f>\d+)\s+@@/g.exec(
        line
      );
    if (!!hunkMatch && !!hunkMatch.groups) {
      if (!!section) {
        chunk1.sections.push(section);
        section = undefined;
      }
      section = {
        hunk: {
          a: hunkMatch.groups["a"] as ChunkSectionHunkType,
          b: parseInt(hunkMatch.groups["b"]) ?? 0,
          c: parseInt(hunkMatch.groups["c"]) ?? 0,
          d: hunkMatch.groups["d"] as ChunkSectionHunkType,
          e: parseInt(hunkMatch.groups["e"]) ?? 0,
          f: parseInt(hunkMatch.groups["f"]) ?? 0,
        },
        changes: [],
      };
      return;
    }
    section?.changes?.push(line);
  });
  if (!!section) {
    chunk1.sections.push(section);
  }
  return chunk1;
};

export default {
  parse_status: parseStatus,
  parse_diff: (rawdiff: string, settings: Settings) => {
    const chunks0 = parsechunks0(rawdiff);
    return chunks0.map((chunk0) => buildchunk1(chunk0, settings));
  },
};
