import express from "express";
import bodyparser from "body-parser";
import fs from "fs";
import path from "path";
import { exec, ExecException } from "child_process";
import md5 from "md5";
import moment from "moment";
import cache from "node-cache";
import fetch from "node-fetch";
import { serverPort, settings, svn, svnUri, uiHelperUri } from "./settings.js";

const sendMessage = (id: string, content: any) => {
  // fetch(`${messageHubUri}/message?id=${id}&sync`, {
  //   headers: {
  //     "Content-Type": "application/json",
  //   },
  //   method: "POST",
  //   body: JSON.stringify({
  //     ...content,
  //     timestamp: moment().format("HH:mm:ss.SSS"),
  //   }),
  // });
};

const app = express();

app.use(bodyparser.urlencoded({ extended: false }));
app.use(bodyparser.json());

app.get("/test", async (_, res) => {
  res.send(": This doesn't means anything to me");
});

app.get("/settings", (_, res) => {
  res.send(settings);
});

app.post("/settings", async (req, res) => {
  const payload = req.body as typeof settings;
  const svn_root = payload?.svn_root ?? "";
  if (!svn_root) {
    res.status(400).json({ error: "svn_root is required" });
    return;
  }
  const result = await fetch(`${svnUri}/fetch/settings`, {
    method: "POST",
    body: JSON.stringify({
      settings: {
        svn_root,
      },
    }),
  });
  if (result.status !== 200) {
    res.status(500).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
    console.error(result);
  } else {
    settings.svn_root_hash = await result.text();
    settings.svn_root = svn_root;
    settings.dark_theme = Boolean(payload?.dark_theme);
  }
  res.end();
});

type SvnCommandCache = {
  data: string;
};
const svn_cache = new cache({ stdTTL: 600, checkperiod: 600 });

const execute = (
  command: string,
  callback?: (
    error: ExecException | null,
    stdout: string,
    stderr: string
  ) => void,
  onClose?: () => void
) => {
  console.log(`executing: [${command}]`);
  const cached = svn_cache.get<SvnCommandCache>(command);
  if (!!cached && !!cached.data) {
    console.log("load from cache");
    callback?.(null, cached.data, "");
    onClose?.();
    return;
  }
  const process = exec(
    command,
    { maxBuffer: 1024 * 1024 },
    (error, stdout, stderror) => {
      callback?.(error, stdout, stderror);
      if (!error && !stderror && !!stdout) {
        svn_cache.set<SvnCommandCache>(command, { data: stdout });
        console.log(`cached: [${command}]`);
      }
    }
  );
  if (!!onClose) {
    process.on("close", () => onClose?.());
  }
};

app.post("/status", (req, res) => {
  res.send(settings.svn_root_hash);
  const params = req.body as { job?: string };
  sendMessage(settings.svn_root_hash, { processing: true, job: params.job });
  try {
    execute(
      `"${svn}" status "${settings.svn_root}"`,
      (error, stdout, stderror) => {
        if (!!error || !!stderror) {
          sendMessage(settings.svn_root_hash, {
            error: error || stderror,
            job: params.job,
          });
          console.error(error || stderror);
        }
        if (!!stdout) {
          sendMessage(settings.svn_root_hash, {
            data: stdout,
            job: params.job,
          });
        }
      },
      () => {
        sendMessage(settings.svn_root_hash, {
          completed: true,
          job: params.job,
        });
      }
    );
  } catch (error) {
    sendMessage(settings.svn_root_hash, { error, job: params.job });
    console.error(error);
  }
});

const validateUrl = (source?: string | null) => {
  if (typeof source !== "string" || source.length === 0) {
    return;
  }
  const url = path.join(settings.svn_root, source).replaceAll("\\", "/");
  if (!fs.existsSync(url)) {
    return;
  }
  return url;
};

app.post("/diff", (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!!url) {
    const id = md5(url);
    const params = req.body as { job?: string };
    res.send(id);
    sendMessage(id, { processing: true, job: params.job });
    try {
      execute(
        `"${svn}" diff "${url}"`,
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            sendMessage(settings.svn_root_hash, {
              error: error || stderror,
              job: params.job,
            });
            console.error(error || stderror);
          }
          if (!!stdout) {
            sendMessage(id, { data: stdout, job: params.job });
          }
        },
        () => {
          sendMessage(id, { completed: true, job: params.job });
        }
      );
    } catch (error) {
      console.error(error);
      sendMessage(id, { error, job: params.job });
    }
  } else {
    console.warn("invalid url");
    res.end();
  }
});

app.post("/unversioned", async (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!url) {
    res.end();
    return;
  }
  const params = req.body as { job?: string };
  if (fs.statSync(url).isDirectory()) {
    console.log("this is D.I.R, how copy? over!");
    sendMessage("", {
      job: params.job,
      error: "this is D.I.R, how copy? over!",
    });
    res.end();
    return;
  }
  const id = md5(url);
  res.send(id);
  sendMessage(id, { processing: true, job: params.job });
  // make sure file is unversioned
  const status = await new Promise<string | null | undefined>((res, rej) => {
    let status = "";
    try {
      execute(
        `"${svn}" status "${url}"`,
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            return;
          }
          if (!!stdout) {
            status = stdout;
          }
        },
        () => {
          res(status);
        }
      );
    } catch (error) {
      console.error(error);
      rej(error);
    }
  });
  if (!status || status[0] !== "?") {
    res.end();
    return;
  }
  try {
    sendMessage(id, {
      data: fs.readFileSync(url, { encoding: "utf-8" }),
      job: params.job,
    });
  } catch (error) {
    console.error(error);
    sendMessage(id, { error, job: params.job });
  } finally {
    sendMessage(id, { completed: true, job: params.job });
  }
});

app.post("/logs", (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!!url) {
    const id = md5(url);
    const params = req.body as { job?: string };
    res.send(id);
    sendMessage(id, { processing: true, job: params.job });
    try {
      execute(
        `"${svn}" log "${url}"`,
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            sendMessage(settings.svn_root_hash, {
              error: error || stderror,
              job: params.job,
            });
            console.error(error || stderror);
          }
          if (!!stdout) {
            sendMessage(id, { data: stdout, job: params.job });
          }
        },
        () => {
          sendMessage(id, { completed: true, job: params.job });
        }
      );
    } catch (error) {
      console.error(error);
      sendMessage(id, { error, job: params.job });
    }
  } else {
    console.warn("invalid url");
    res.end();
  }
});

app.post("/logdiff", async (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!!url) {
    const params = req.body as { job?: string; n?: number; m?: number };
    const n = !params.n || params.n <= 0 ? -1 : params.n;
    const m = !params.m || params.m < 0 ? -1 : params.m;
    if (n < 0) {
      console.warn("invalid range", { n, m });
      res.end();
      return;
    }
    const id = md5(url);
    res.send(id);
    sendMessage(id, { processing: true, job: params.job });
    try {
      execute(
        `"${svn}" diff -r ${n}${m >= 0 ? `:${m}` : ""} "${url}"`,
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            sendMessage(settings.svn_root_hash, {
              error: error || stderror,
              job: params.job,
            });
            console.error(error || stderror);
          }
          if (!!stdout) {
            sendMessage(id, { data: stdout, job: params.job });
          }
        },
        () => {
          sendMessage(id, { completed: true, job: params.job });
        }
      );
    } catch (error) {
      console.error(error);
      sendMessage(id, { error, job: params.job });
    }
  } else {
    console.warn("invalid url");
    res.end();
  }
});

app.post("/opendir", async (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!url) {
    res.end();
    return;
  }
  await fetch(`${uiHelperUri}/opendir?path=${encodeURI(url)}`, {
    method: "POST",
  });
  res.end();
});

app.listen(serverPort, () => {
  console.log(`Server is Running on ${serverPort}`);
});
