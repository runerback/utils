import * as signalr from "@microsoft/signalr";
import { Observable, Subject } from "rxjs";

const onMessage = new Subject<Message>();
const connection = new signalr.HubConnectionBuilder()
  .withUrl("/api/messages")
  .build();
connection.on("message", (data) => {
  const message = JSON.parse(data) as Message;
  if (!message || !message.id) {
    return;
  }
  console.log({ message });
  onMessage.next(message);
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
  return (await res.json()) as SvnSettings;
};

const update_settings = async (settings: SvnSettings) => {
  await fetch("/api/server/settings", {
    headers: {
      "Content-Type": "application/json",
    },
    method: "POST",
    body: JSON.stringify(settings),
  });
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
  console.log("fetch_diff ", source);
  const res = await fetch(
    "/api/server/diff" + (!!source ? `?path=${encodeURI(source)}` : ""),
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

const pick_dir = async (init?: string) => {
  const res = await fetch(
    "/api/uihelper/pickdir" + (!!init ? `?path=${encodeURI(init)}` : ""),
    {
      method: "POST",
    }
  );
  return await res.text();
};

const request = async <TReq = never, TRes = never>(
  call: (req?: TReq) => Promise<TRes | undefined>,
  req?: TReq
) => {
  try {
    return await call(req);
  } catch (message) {
    console.error(message);
  }
};

export default {
  test_server: () => request(test_server),
  get_settings: () => request(get_settings),
  update_settings: (settings: SvnSettings) =>
    request((settings) => update_settings(settings!), settings),
  pick_dir: (init?: string) => request((init) => pick_dir(init), init),
  fetch_status: () => request(()=> fetch_status("FETCH_STATUS")),
  fetch_diff: (source: string) =>
    request((source) => fetch_diff(source, "FETCH_DIFFS"), source),
  onMessage: onMessage as Observable<Message>,
};
