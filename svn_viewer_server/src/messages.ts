import moment from "moment";
import { messageHubUri, settings } from "./settings.js";
import svnparser from "./svnparser.js";
import cache from "./cache.js";

export const sendMessage = (id: string, content: any) => {
  fetch(`${messageHubUri}/message?id=${id}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      ...content,
      timestamp: moment().format("HH:mm:ss.SSS"),
    }),
  });
};

const preprocess_status = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_status(data, settings);
  sendMessage(id, { data: parsed, job });
  cache.fetch_status.set(id, parsed, job);
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

const preprocess_diffs = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_diff(data, settings);
  sendMessage(id, { data: parsed, job });
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

const preprocess_logs = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_logs(data);
  sendMessage(id, { data: parsed, job });
  cache.fetch_logs.set(id, parsed, job);
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

const preprocess_log_diffs = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_diff(data, settings);
  sendMessage(id, { data: parsed, job });
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

const preprocess_tree = (id: string, data: string, job: Job) => {
  const raw = JSON.parse(data) as {
    props?: string;
    nodes?: { name: string; dir?: boolean; children?: boolean }[];
  };
  const props = !!raw?.props ? svnparser.parse_props(raw.props) : {};
  const ignores = [
    ...Object.entries(props)
      .filter(
        ([name]) => name === "svn:global-ignores" || name === "svn:ignore"
      )
      .flatMap(([, items]) => items),
    ".svn",
  ];
  const nodes = raw?.nodes ?? [];
  for (let i = nodes.length - 1; i >= 0; i--) {
    const node = nodes[i];
    if (!!node.dir) {
      if (ignores.includes(node.name)) {
        nodes.splice(i, 1);
        continue;
      }
    } else {
      const ext = node.name.substring(node.name.lastIndexOf("."));
      if (!!ext) {
        const test = "*" + ext;
        if (ignores.includes(test)) {
          nodes.splice(i, 1);
          continue;
        }
      }
    }
  }
  sendMessage(id, { data: nodes, job });
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

const preprocess_info = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_info(data);
  if (!!parsed) {
    console.log({ parsed });
    sendMessage(id, { data: parsed, job });
    cache.fetch_info.set(id, parsed, job);
  }
  global.setTimeout(() => {
    console.log("[preprocess_info]", "at this moment, the id is: ", id);
    sendMessage(id, { completed: true, job });
  }, 30);
};

const preprocess_rev_logs = (id: string, data: string, job: Job) => {
  const parsed = svnparser.parse_rev_logs(data);
  if (!!parsed) {
    console.log({ parsed });
    sendMessage(id, { data: parsed, job });
  }
  global.setTimeout(() => sendMessage(id, { completed: true, job }), 30);
};

export default (message: Message) => {
  if (!!message.content) {
    const content = JSON.parse(message.content) as MessageContent;
    if (!!content && !!content.timestamp && !!content.data && !!content.job) {
      try {
        switch (content.job) {
          case "FETCH_SETTINGS":
            {
              const value = content.data as Settings;
              settings.svn_root = value.svn_root;
              settings.svn_repo = value.svn_repo;
              settings.svn_rev = value.svn_rev;
              settings.fetched = true;
              global.setTimeout(
                () =>
                  sendMessage(message.id, {
                    completed: true,
                    job: content.job,
                  }),
                30
              );
            }
            break;
          case "FETCH_STATUS":
            preprocess_status(message.id, content.data as string, content.job);
            break;
          case "FETCH_DIFFS":
            preprocess_diffs(message.id, content.data as string, content.job);
            break;
          case "FETCH_LOGS":
            preprocess_logs(message.id, content.data as string, content.job);
            break;
          case "FETCH_LOG_DIFFS":
            preprocess_log_diffs(
              message.id,
              content.data as string,
              content.job
            );
            break;
          case "FETCH_TREE":
            preprocess_tree(message.id, content.data as string, content.job);
            break;
          case "FETCH_INFO":
            preprocess_info(message.id, content.data as string, content.job);
            break;
          case "FETCH_REVISION_LOGS":
            preprocess_rev_logs(
              message.id,
              content.data as string,
              content.job
            );
            break;
          default:
            break;
        }
      } catch (error) {
        console.warn("preprocessing failed", error);
      }
    } else {
      console.warn("message content not suitable", content);
    }
  } else {
    console.warn("message has no content");
  }
};
