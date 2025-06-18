import moment from "moment";
import { messageHubUri, settings } from "./settings.js";
import svnparser from "./svnparser.js";

const sendMessage = (id: string, content: any) => {
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

export default (message: Message) => {
  console.log("handle message: ", typeof message.content);
  if (!!message.content) {
    const content = JSON.parse(message.content) as MessageContent;
    if (!!content && !!content.timestamp && !!content.data && !!content.job) {
      try {
        switch (content.job) {
          case "FETCH_STATUS":
            preprocess_status(message.id, content.data, content.job);
            break;
          case "FETCH_DIFFS":
            preprocess_diffs(message.id, content.data, content.job);
            break;
          case "FETCH_LOGS":
            preprocess_logs(message.id, content.data, content.job);
            break;
          case "FETCH_LOG_DIFFS":
            preprocess_log_diffs(message.id, content.data, content.job);
            break;
          case "FETCH_TREE":
            preprocess_tree(message.id, content.data, content.job);
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
