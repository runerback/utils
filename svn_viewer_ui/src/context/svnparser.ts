const parseLogs = (rawLogs: string) => {
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

export default {
  parse_logs: parseLogs,
};
