const NO_CHANGE_LIST_NAME = "no-change-list";
const IGNORE_ON_COMMIT_NAME = "ignore-on-commit";
const changeListPriority = (name: string) => {
  switch (name) {
    case NO_CHANGE_LIST_NAME:
      return -1;
    case IGNORE_ON_COMMIT_NAME:
      return 1;
    default:
      return 0;
  }
};

const parse_status = (rawStatus: string, settings: Settings): SvnStatus[] => {
  if (!rawStatus) {
    return [];
  }
  const status = Array<SvnStatus>();
  const lines = rawStatus.split(/\r|\n/gs).filter(Boolean);
  let changelist = NO_CHANGE_LIST_NAME;
  const changes = Array<SvnStatusItem>();
  const flush = () => {
    changes.sort((a, b) => a.state.localeCompare(b.state));
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
  status.sort((a, b) => {
    const p0 = changeListPriority(a.changelist);
    const p1 = changeListPriority(b.changelist);
    if (p0 === p1 && p1 === 0) {
      return a.changelist.localeCompare(b.changelist);
    } else {
      return p0 - p1;
    }
  });
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
        chunks.push({ ...chunk });
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
    section?.changes?.push(line); //.replaceAll(" ", "\u00B7"));
  });
  if (!!section) {
    chunk1.sections.push(section);
  }
  return chunk1;
};

const parse_logs = (rawLogs: string) => {
  const lines = rawLogs.split(/\r|\n/gs).filter(Boolean);
  const logs = Array<SvnLog>();
  let infoLine = false;
  let log: SvnLog | undefined;
  for (let i = 0, j = lines.length; i < j; i++) {
    const line = lines[i];
    if (/(-{60,})/.test(line)) {
      infoLine = true;
      if (!!log) {
        logs.push({ ...log });
      }
      log = undefined;
      continue;
    }
    if (infoLine) {
      infoLine = false;
      const infoMatch =
        /.*?r(?<revision>\d+)\s*\|\s*(?<author>.+)\s*\|\s*(?<timestamp>.+)\s*\|\s*(?<line>\d+)\s*line.*?/g.exec(
          line
        );
      if (!!infoMatch && !!infoMatch.groups) {
        log = {
          revision: infoMatch.groups["revision"],
          author: infoMatch.groups["author"],
          timestamp: infoMatch.groups["timestamp"],
          changes: parseInt(infoMatch.groups["line"]) ?? 0,
        };
      }
    } else {
      if (!!log) {
        log.message = line;
      }
    }
  }
  if (!!log) {
    logs.push({ ...log });
  }

  return logs;
};

const parse_props = (raw: string) => {
  const result: Record<string, string[]> = {};
  const lines = raw.split(/\r|\n/gs).filter(Boolean);
  let currentLines = Array<string>();
  lines.forEach((line) => {
    if (line[0] !== " ") {
      return;
    }
    if (line[1] === " ") {
      if (line[2] === " " && line[3] === " ") {
        const prop = line.trim();
        if (!!prop) {
          currentLines.push(line.trim());
        }
      } else {
        result[line.trim()] = currentLines = [];
      }
    }
  });
  return result;
};

const parse_info = (raw: string): SvnTreeNodeInfo | undefined => {
  if (!raw) {
    return undefined;
  }
  const lines = raw.split(/\r|\n/gs).filter(Boolean);
  if (!lines) {
    return undefined;
  }
  const result: SvnTreeNodeInfo = {};
  let anyMatched = false;
  lines.forEach((line) => {
    const titleIdx = line.indexOf(":");
    const value = line.substring(titleIdx + 1).trim();
    if (!value) {
      return;
    }
    const title = line.substring(0, titleIdx).trim().toLocaleLowerCase();
    switch (title) {
      case "revision":
        result.revision = value;
        anyMatched = true;
        break;
      case "last changed author":
        result.lastChangedAuthor = value;
        anyMatched = true;
        break;
      case "last changed rev":
        result.lastChangedRev = value;
        anyMatched = true;
        break;
      case "last changed date":
        result.lastChangedTime = value.replace(/\s+\(.+\)/g, "");
        anyMatched = true;
        break;
      case "status":
        result.status = value;
        anyMatched = true;
        break;
      default:
        break;
    }
  });
  return anyMatched ? result : undefined;
};

const parse_rev_logs = (raw: string): SvnRevStatusItem[] => {
  if (!raw) {
    return [];
  }
  const lines = raw.split(/\r|\n/gs).filter(Boolean);
  if (!lines) {
    return [];
  }
  const result = Array<SvnRevStatusItem>();
  let receivingPaths = false;
  for (let i = 0, j = lines.length; i < j; i++) {
    const line = lines[i];
    if (receivingPaths) {
      if (!line) {
        receivingPaths = false;
        break;
      }
      const match = /\s*(?<status>[A-Z])\s+(?<source>.+)/g.exec(line);
      if (!!match && !!match.groups) {
        const item: SvnRevStatusItem = {
          state: match.groups["status"],
          source: match.groups["source"],
        };
        const froms = item.source.split(" ");
        if (!!froms && froms.length === 3 && froms[1] === "(from") {
          const fromParts = froms[2].split(":");
          if (!!fromParts && fromParts.length === 2) {
            item.source = froms[0];
            item.from = fromParts[0];
            item.rev = parseInt(fromParts[1].slice(0, -1)) ?? undefined;
          }
        }
        result.push(item);
      }
    } else {
      if (line === "Changed paths:") {
        receivingPaths = true;
        continue;
      }
    }
  }
  return result;
};

export default {
  parse_status,
  parse_diff: (rawdiff: string, settings: Settings) => {
    const chunks0 = parsechunks0(rawdiff);
    return chunks0.map((chunk0) => buildchunk1(chunk0, settings));
  },
  parse_logs,
  parse_props,
  parse_info,
  parse_rev_logs,
};
