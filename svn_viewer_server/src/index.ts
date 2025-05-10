import express from "express";
import bodyparser from "body-parser";
import fs from "fs";
import path from "path";
import { exec, ExecException } from "child_process";
import md5 from "md5";
import moment from "moment";

const messageHubUri = process.env["services__messages__http__0"];
const uiHelperUri = process.env["services__uihelper__http__0"];
const svn = await new Promise<string | undefined>((resolve, reject) => {
  try {
    const ps1 = exec(
      "powershell -c (Get-Command svn).Source",
      (error, stdout, stderror) => {
        if (!!stdout) {
          return resolve(stdout.trimEnd().replaceAll("\\", "/"));
        }
        if (!!error || !!stderror) {
          return reject(error || stderror);
        }
      }
    );
    ps1.on("close", () => {
      resolve(undefined);
    });
  } catch (error) {
    reject(error);
  }
});
if (!svn) {
  console.warn("svn not found");
} else {
  console.log("svn", [svn]);
}

const settings = {
  svn_root: "",
  svn_root_hash: "",
  dark_theme: false,
};

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

const app = express();

app.use(bodyparser.urlencoded({ extended: false }));
app.use(bodyparser.json());

app.get("/test", (_, res) => {
  res.send(": This doesn't means anything to me");
});

app.get("/settings", (_, res) => {
  res.send(settings);
});

app.post("/settings", (req, res) => {
  res.end();
  const payload = req.body as typeof settings;
  settings.svn_root = payload?.svn_root ?? "";
  settings.dark_theme = Boolean(payload?.dark_theme);
  if (!!svn && !!settings.svn_root && fs.existsSync(settings.svn_root)) {
    settings.svn_root_hash = md5(settings.svn_root);
  }
});

const execute = (
  command: string,
  callback?: (
    error: ExecException | null,
    stdout: string,
    stderr: string
  ) => void
) => {
  console.log(`executing: [${command}]`);
  return exec(command, { maxBuffer: 1024 * 1024 }, callback);
};

app.post("/status", (req, res) => {
  res.send(settings.svn_root_hash);
  const params = req.body as { job?: string };
  sendMessage(settings.svn_root_hash, { processing: true, job: params.job });
  try {
    const svn_status = execute(
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
      }
    );
    svn_status.on("close", () => {
      sendMessage(settings.svn_root_hash, { completed: true, job: params.job });
    });
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
    try {
      const svndiff = execute(
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
        }
      );
      sendMessage(id, { processing: true, job: params.job });
      svndiff.on("close", () => {
        sendMessage(id, { completed: true, job: params.job });
      });
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
      const svnstatus = execute(
        `"${svn}" status "${url}"`,
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            return;
          }
          if (!!stdout) {
            status = stdout;
          }
        }
      );
      svnstatus.on("close", () => {
        res(status);
      });
    } catch (error) {
      console.error(error);
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

app.listen(process.env["SERVER_PORT"], () => {
  console.log(`Server is Running on ${process.env["SERVER_PORT"]}`);
});
