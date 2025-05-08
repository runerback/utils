import express from "express";
import bodyparser from "body-parser";
import fs from "fs";
import path from "path";
import { exec } from "child_process";
import md5 from "md5";
import moment from "moment";

const messageHubUri = process.env["services__messages__http__0"];
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
  show_changelist: false,
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
  res.send(": This means nothing to me");
});

app.get("/settings", (_, res) => {
  res.send(settings);
});

app.post("/settings", (req, res) => {
  res.end();
  const payload = req.body as typeof settings;
  settings.svn_root = payload?.svn_root ?? "";
  if (!!svn && !!settings.svn_root && fs.existsSync(settings.svn_root)) {
    settings.svn_root_hash = md5(settings.svn_root);
  }
});

app.post("/status", (req, res) => {
  res.send(settings.svn_root_hash);
  const params = req.body as { job?: string };
  sendMessage(settings.svn_root_hash, { processing: true, job: params.job });
  try {
    const svn_status = exec(
      `"${svn}" status "${settings.svn_root}"`,
      { maxBuffer: 1024 * 1024 },
      (error, stdout, stderror) => {
        if (!!error || !!stderror) {
          sendMessage(settings.svn_root_hash, { error, job: params.job });
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
  console.log("validating", url);
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
      const svndiff = exec(
        `"${svn}" diff "${url}"`,
        { maxBuffer: 1024 * 1024 },
        (error, stdout, stderror) => {
          if (!!error || !!stderror) {
            sendMessage(id, { error, job: params.job });
          }
          if (!!stdout) {
            sendMessage(id, { data: stdout, job: params.job });
          }
        }
      );
      svndiff.on("close", () => {
        sendMessage(id, { completed: true, job: params.job });
      });
    } catch (error) {
      sendMessage(id, { error, job: params.job });
      console.error(error);
    }
  } else {
    console.log("invalid url");
    res.end();
  }
});

app.post("/unversioned", (req, res) => {
  const url = validateUrl(req.body as string);
  if (!url) {
    res.end();
    return;
  }
  let status = "";
  try {
    exec(
      `"${svn}" status "${url}"`,
      { maxBuffer: 1024 * 1024 },
      (error, stdout, stderror) => {
        if (!!error || !!stderror) {
          return;
        }
        if (!!stdout) {
          status = stdout;
        }
      }
    );
  } catch (error) {
    console.error(error);
  }
  if (!status || status[0] !== "?") {
    res.end();
    return;
  }
  res.send(fs.readFileSync(url, { encoding: "utf-8" }));
});

app.listen(process.env["SERVER_PORT"], () => {
  console.log(`Server is Running on ${process.env["SERVER_PORT"]}`);
});
