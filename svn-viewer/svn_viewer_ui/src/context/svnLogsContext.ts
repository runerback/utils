import { createContext } from "preact";
import network from "./network";
import { signal, type ReadonlySignal } from "@preact/signals-react";
import moment from "moment";
import { publishSvnDiffStream } from "./svnDiffContext";

export interface ISvnLogsContext {
  readonly status$: ReadonlySignal<SvnStatusItem | undefined>;
  readonly show$: ReadonlySignal<boolean>;
  show(): void;
  close(): void;
}

export const SvnLogsContext = createContext<ISvnLogsContext>(null!);

const status$ = signal<SvnStatusItem>();
const show$ = signal(false);

export const provideSvnLogs = async (
  status: SvnStatusItem,
  flush?: boolean
) => {
  const result = await network.fetch_logs(status.source, flush);
  if (!!result) {
    status$.value = status;
    show$.value = true;
    if (!!result.payload) {
      const id = moment().valueOf().toString();
      setTimeout(() => {
        publishSvnDiffStream({
          id,
          job: "FETCH_LOGS",
          logs: [
            {
              status,
              logs: result.payload ?? [],
            },
          ],
          finished: true,
        });
      }, 0);
      return id;
    } else if (!!result.id) {
      return result.id;
    }
  }
};

export default (): ISvnLogsContext => ({
  status$,
  show$,
  show: () => {
    show$.value = true;
  },
  close: () => {
    show$.value = false;
  },
});
