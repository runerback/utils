import express from "express";
import bodyparser from "body-parser";
import fs from "fs";
import path from "path";
import * as signalr from "@microsoft/signalr";
import {
  messageHubUri,
  serverPort,
  settings,
  svnUri,
  uiHelperUri,
} from "./settings.js";
import onMessage from "./messages.js";

const connection = new signalr.HubConnectionBuilder()
  .withUrl(`${messageHubUri}/messages`)
  .build();
connection.on("pre_message", (data) => {
  console.log({ pre_message: data });
  const message = data as Message;
  if (!message || !message.id) {
    return;
  }
  onMessage(message);
});
connection.onclose((error) => {
  console.log("hub closed", error);
  if (!!error) {
    connection
      .start()
      .then(() => console.log("connected to hub"))
      .catch((error) => console.log("connecting to hub failed", error));
  }
});
connection.onreconnecting((error) => {
  console.log("reconnecting to hub", error);
});
connection.onreconnected((id) => {
  console.log("reconnected to hub", id);
});
connection
  .start()
  .then(() => console.log("connected to hub"))
  .catch((error) => console.log("connecting to hub failed", error));

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
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      settings: {
        svn_root: svn_root.replaceAll("\\", "/"),
      },
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  } else {
    settings.svn_root_hash = await result.text();
    settings.svn_root = svn_root;
    settings.dark_theme = Boolean(payload?.dark_theme);
  }
  res.end();
});

app.post("/status", async (req, res) => {
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/status`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  }
  res.end();
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

app.post("/diff", async (req, res) => {
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/diff`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      path,
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  }
  res.send(await result.json());
  res.end();
});

app.post("/unversioned", async (req, res) => {
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/unversioned`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      path,
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  }
  res.send(await result.json());
  res.end();
});

app.post("/logs", async (req, res) => {
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/logs`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      path,
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  }
  res.send(await result.json());
  res.end();
});

app.post("/logdiff", async (req, res) => {
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string; n?: number; m?: number };
  const result = await fetch(`${svnUri}/fetch/logdiffs`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      path,
      n: params.n ?? 0,
      m: params.m,
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res.status(400).json({
      error: {
        status: result.status,
        statusText: result.statusText,
      },
    });
  }
  res.send(await result.json());
  res.end();
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
