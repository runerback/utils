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

export default (message: Message) => {
  console.log("handle message: ", typeof message.content);
  if (!!message.content) {
    const content = JSON.parse(message.content) as MessageContent;
    if (!!content && !!content.timestamp && !!content.data && !!content.job) {
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
        default:
          break;
      }
    }
  }
};
