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
import onMessage, { sendMessage } from "./messages.js";
import cache from "./cache.js";

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
  res.send(": This doesn't means anything to me").end();
});

app.get("/settings", (_, res) => {
  if (!settings.fetched) {
    res.status(400).send("settings not fetched yet").end();
    return;
  }
  res.send(settings).end();
});

app.post("/settings", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  settings.fetched = undefined;
  const result = await fetch(`${svnUri}/fetch/settings`, {
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  settings.dark_theme = Boolean(req.query?.["dark"]);
  res.send(await result.json()).end();
});

app.post("/status", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const params = req.body as { job?: string; flush?: boolean };
  const body = JSON.stringify({
    job: params.job,
  });
  const cached = cache.fetch_status.get(body);
  if (!!cached && !!cached.payload) {
    if (!!params.flush) {
      cache.fetch_status.reset(body);
    } else {
      console.log("[💾cache] loaded", cached);
      res.send({ payload: cached.payload, id: cached.id }).end();
      return;
    }
  }
  const result = await fetch(`${svnUri}/fetch/status`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body,
  });
  if (result.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  const id = await result.json();
  if ((!cached || !!params.flush) && !!id) {
    cache.fetch_status.pending(body, id);
  }
  res.send({ id }).end();
});

app.post("/status/file", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/status/file`, {
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/remote/file", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/remote/file`, {
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/diff", async (req, res) => {
  console.log({ url: req.url, body: req.body });
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/unversioned", async (req, res) => {
  console.log({ url: req.url, body: req.body });
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/logs", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string; flush?: boolean };
  const body = JSON.stringify({
    path,
    job: params.job,
  });
  const cached = cache.fetch_logs.get(body);
  if (!!cached && !!cached.payload) {
    if (!!params.flush) {
      cache.fetch_logs.reset(body);
    } else {
      console.log("[💾cache] loaded", cached);
      res.send({ payload: cached.payload, id: cached.id }).end();
      return;
    }
  }
  const result = await fetch(`${svnUri}/fetch/logs`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body,
  });
  if (result.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  const id = await result.json();
  if ((!cached || !!params.flush) && !!id) {
    cache.fetch_logs.pending(body, id);
  }
  res.send({ id }).end();
});

app.post("/logdiff", async (req, res) => {
  console.log({ url: req.url, body: req.body });
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/tree", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { job?: string };
  const result = await fetch(`${svnUri}/fetch/tree`, {
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
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/info", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as {
    status?: boolean;
    job?: string;
    flush?: boolean;
  };
  const body = JSON.stringify({
    path,
    status: params.status,
    job: params.job,
  });
  const cached = cache.fetch_info.get(body);
  if (!!cached && !!cached.payload) {
    if (!!params.flush) {
      cache.fetch_info.reset(body);
    } else {
      console.log("[💾cache] loaded", cached);
      res.send({ payload: cached.payload, id: cached.id }).end();
      return;
    }
  }
  const result = await fetch(`${svnUri}/fetch/info`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body,
  });
  if (result.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  const id = await result.json();
  if ((!cached || !!params.flush) && !!id) {
    cache.fetch_info.pending(body, id);
  }
  res.send({ id }).end();
});

app.post("/rev/logs", async (req, res) => {
  console.log({ url: req.url, body: req.body });
  const path = req.query?.["path"] as string;
  const params = req.body as { rev?: number; job?: string };
  const result = await fetch(`${svnUri}/fetch/rev/logs`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      path,
      rev: params.rev,
      job: params.job,
    }),
  });
  if (result.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  res.send(await result.json()).end();
});

app.post("/repo/browser", async (req, res) => {
  console.log({ url: req.url });
  const result = await fetch(`${svnUri}/repo/browser`, {
    method: "POST",
  });
  if (result.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: result.status,
          statusText: result.statusText,
        },
      })
      .end();
    return;
  }
  const args = ((await result.json()) as { args?: string[] })?.args;
  if (!args || args.length < 1) {
    res.json({}).end();
    return;
  }
  const helperRes = await fetch(`${uiHelperUri}/win/exec`, {
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    method: "POST",
    body: JSON.stringify({
      executable: args[0],
      args: args.slice(1),
    }),
  });
  if (helperRes.status !== 200) {
    res
      .status(400)
      .json({
        error: {
          status: helperRes.status,
          statusText: helperRes.statusText,
        },
      })
      .end();
    return;
  }
  res.json({ succeed: true }).end();
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

app.post("/opendir", async (req, res) => {
  const url = validateUrl(req.query?.["path"] as string);
  if (!url) {
    res.status(400).send("invalid path").end();
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
