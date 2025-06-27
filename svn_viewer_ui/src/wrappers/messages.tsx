import MessageContextProvider, {
  MessageContext,
} from "../context/messageContext";
import type { NotificationInstance } from "antd/es/notification/interface";
import Keyboard from "./keyboard";
import { useContext, useMemo } from "preact/hooks";
import { useSignalEffect } from "@preact/signals-react";
import { filter } from "rxjs";
import { StatusContext } from "../context/statusContext";
import network from "../context/network";
import { publishSvnDiffStream } from "../context/svnDiffContext";
import { publishSvnInfoStream } from "../context/svnInfoContext";
import { publishSvnLogDiffsStream } from "../context/svnLogDiffsContext";
import { publishSvnTreeStream } from "../context/svnTreeContext";
import { publishSvnRevLogsStream } from "../context/svnRevLogContext";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const statusContext = useContext(StatusContext);
  useSignalEffect(() => {
    network.errors$.subscribe((e: any) => {
      props.notify(`${e}`, "error");
      statusContext.idle();
    });
  });
  const messageContext = useMemo(() => MessageContextProvider(), []);
  useSignalEffect(() => {
    messageContext.stream$
      .pipe(filter((it) => !!it && !!it.content))
      .subscribe((e) => {
        const content = e.content!;
        if (!!content.processing) {
          statusContext.busy();
          props.notify(`${content.job ?? "Something"} started`, "success");
        } else if (!!content.error) {
          console.warn(content.error);
          statusContext.idle();
          props.notify(
            `${content.job ?? "Something"} failed: ${
              content.error ?? "Unknown Error"
            }`,
            "warning"
          );
        } else if (!!content.completed) {
          statusContext.idle();
          props.notify(`${content.job ?? "Something"} finished`, "success");
          const id = e.id;
          switch (content.job) {
            case "FETCH_DIFFS":
            case "FETCH_UNVERSIONED":
            case "FETCH_FILE_REMOTE":
            case "FETCH_LOGS":
              publishSvnDiffStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            case "FETCH_LOG_DIFFS":
              publishSvnLogDiffsStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            case "FETCH_TREE":
              publishSvnTreeStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            case "FETCH_INFO":
              publishSvnInfoStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            case "FETCH_REVISION_LOGS":
              publishSvnRevLogsStream({
                id,
                job: content.job,
                finished: true,
              });
              break;
            default:
              break;
          }
        } else if (!!content.data) {
          const id = e.id;
          switch (content.job) {
            case "FETCH_DIFFS":
              publishSvnDiffStream({
                id,
                job: content.job,
                chunks: (content.data as Chunk1[]) ?? [],
              });
              break;
            case "FETCH_UNVERSIONED":
              publishSvnDiffStream({
                id,
                job: content.job,
                unversioned: (content.data as string)
                  .split(/\r|\n/g)
                  .filter(Boolean),
              });
              break;
            case "FETCH_FILE_REMOTE":
              publishSvnDiffStream({
                id,
                job: content.job,
                missing: (content.data as string)
                  .split(/\r|\n/g)
                  .filter(Boolean),
              });
              break;
            case "FETCH_LOGS": {
              publishSvnDiffStream({
                id,
                job: content.job,
                logs: [
                  {
                    logs: (content.data as SvnLog[]) ?? [],
                  },
                ],
              });
              break;
            }
            case "FETCH_LOG_DIFFS": {
              publishSvnLogDiffsStream({
                id,
                job: content.job,
                chunks: (content.data as Chunk1[]) ?? [],
              });
              break;
            }
            case "FETCH_TREE": {
              publishSvnTreeStream({
                id,
                job: content.job,
                nodes: (
                  (content.data as {
                    name: string;
                    dir?: boolean;
                    children?: boolean;
                  }[]) ?? []
                ).map((it) => ({
                  name: it.name,
                  kind: !!it.dir ? "DIR" : "FILE",
                  expandable: it.children,
                })),
              });
              break;
            }
            case "FETCH_INFO":
              publishSvnInfoStream({
                id,
                job: content.job,
                info: content.data as SvnTreeNodeInfo,
              });
              break;
            case "FETCH_REVISION_LOGS":
              publishSvnRevLogsStream({
                id,
                job: content.job,
                items: content.data as SvnRevStatusItem[],
              });
              break;
            default:
              break;
          }
        }
      });
  });
  return (
    <MessageContext.Provider value={messageContext}>
      <Keyboard {...props} />
    </MessageContext.Provider>
  );
};
