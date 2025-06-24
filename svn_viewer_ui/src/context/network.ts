import * as signalr from "@microsoft/signalr";
import { Subject } from "rxjs";

const messages$ = new Subject<Message>();
const connection = new signalr.HubConnectionBuilder()
  .withUrl("/api/messages")
  .build();
connection.on("message", (data) => {
  const message = data as Message;
  if (!message || !message.id) {
    return;
  }
  console.log({ message });
  messages$.next(message);
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

const test_server = async () => {
  const res = await fetch("/api/server/test", {
    method: "GET",
  });
  return await res.text();
};

const get_settings = async () => {
  const res = await fetch("/api/server/settings", {
    method: "GET",
  });
  if (res.status !== 200) {
    throw await res.text();
  }
  return (await res.json()) as Settings;
};

const update_settings = async (settings: SettingsRequest, job?: Job) => {
  const res = await fetch(
    `/api/server/settings?path=${encodeURI(settings.svn_root)}` +
      (!!settings.dark_theme ? "&dark" : ""),
    {
      headers: {
        "Content-Type": "application/json",
      },
      method: "POST",
      body: JSON.stringify({ job }),
    }
  );
  return await res.text();
};

const fetch_status = async (job?: Job) => {
  const res = await fetch("/api/server/status", {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_diff = async (source?: string, job?: Job) => {
  if (!source) {
    return;
  }
  const res = await fetch(`/api/server/diff?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_logs = async (source: string, job?: Job) => {
  const res = await fetch(`/api/server/logs?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_log_diffs = async (
  source?: string,
  job?: Job,
  params?: FetchLogDiffsRange
) => {
  if (!source || !params || !params.n) {
    return;
  }
  const res = await fetch(`/api/server/logdiff?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job, n: params.n, m: params.m }),
  });
  return await res.text();
};

const fetch_unversioned = async (source?: string, job?: Job) => {
  if (!source) {
    return;
  }
  const res = await fetch(`/api/server/unversioned?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_file_status = async (source?: string, job?: Job) => {
  if (!source) {
    return;
  }
  const res = await fetch(`/api/server/status/file?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_file_remote = async (source?: string, job?: Job) => {
  if (!source) {
    return;
  }
  const res = await fetch(`/api/server/remote/file?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_tree = async (source?: string, job?: Job) => {
  const res = await fetch(`/api/server/tree?path=${encodeURI(source ?? "/")}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ job }),
  });
  return await res.text();
};

const fetch_info = async (source: string, status?: boolean, job?: Job) => {
  const res = await fetch(`/api/server/info?path=${encodeURI(source)}`, {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify({ status, job }),
  });
  return await res.text();
};

const pick_dir = async (init?: string) => {
  const res = await fetch(
    "/api/uihelper/pickdir" + (!!init ? `?path=${encodeURI(init)}` : ""),
    {
      method: "POST",
    }
  );
  return await res.text();
};

const open_in_dir = async (path?: string) => {
  if (!path) {
    return;
  }
  await fetch(`/api/server/opendir?path=${encodeURI(path)}`, {
    method: "POST",
  });
};

const open_repo_browser = async () => {
  const res = await fetch("/api/server/repo/browser", {
    method: "POST",
  });
  if (res.status !== 200) {
    return { error: res.statusText };
  }
  return (await res.json()) as { succeed?: boolean };
};

const errors$ = new Subject<any>();

const request = async <TReq = never, TRes = never>(
  call: (req?: TReq) => Promise<TRes | undefined>,
  req?: TReq
) => {
  try {
    return await call(req);
  } catch (error) {
    errors$.next(error);
  }
};

const request2 = async <TReq1 = never, TReq2 = never, TRes = never>(
  call: (req1?: TReq1, req2?: TReq2) => Promise<TRes | undefined>,
  req1?: TReq1,
  req2?: TReq2
) => {
  try {
    return await call(req1, req2);
  } catch (error) {
    errors$.next(error);
  }
};

export default {
  messages$,
  errors$,
  test_server: () => request(test_server),
  get_settings: () => request(get_settings),
  update_settings: (settings: SettingsRequest) =>
    request(
      (settings) => update_settings(settings!, "FETCH_SETTINGS"),
      settings
    ),
  open_repo_browser: () => request(open_repo_browser),
  fetch_status: () => request(() => fetch_status("FETCH_STATUS")),
  fetch_diff: (source: string) =>
    request((source) => fetch_diff(source, "FETCH_DIFFS"), source),
  fetch_unversioned: (source: string) =>
    request((source) => fetch_unversioned(source, "FETCH_UNVERSIONED"), source),
  fetch_file_remote: (source: string) =>
    request((source) => fetch_file_remote(source, "FETCH_FILE_REMOTE"), source),
  fetch_logs: (source: string) =>
    request((source) => fetch_logs(source!, "FETCH_LOGS"), source),
  fetch_info: (source: string, status?: boolean) =>
    request2(
      (source, status) => fetch_info(source!, status, "FETCH_INFO"),
      source,
      status
    ),
  fetch_log_diffs: (source?: string, params?: FetchLogDiffsRange) =>
    request2(
      (source, params) => fetch_log_diffs(source, "FETCH_LOG_DIFFS", params),
      source,
      params
    ),
  fetch_file_status: (source: string) =>
    request((source) => fetch_file_status(source, "FETCH_FILE_STATUS"), source),
  fetch_tree: (source?: string) =>
    request((source) => fetch_tree(source, "FETCH_TREE"), source),
  pick_dir: (init?: string) => request((init) => pick_dir(init), init),
  open_in_dir: (path?: string) => request((path) => open_in_dir(path), path),
} as INetwork;
