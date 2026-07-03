import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnLogDiffsContext {
  readonly stream$: Observable<SvnLogDiffsStream>;
  provide: (
    status: SvnStatusItem,
    range: FetchLogDiffsRange
  ) => Promise<string | null | undefined>;
}

export const SvnLogDiffsContext =
  createContext<ISvnLogDiffsContext>(null!);

const stream$ = new Subject<SvnLogDiffsStream>();

export const publishSvnLogDiffsStream = (stream: SvnLogDiffsStream) => {
  stream$.next(stream);
};

export default (): ISvnLogDiffsContext => ({
  stream$,
  provide: (status, range) => {
    switch (status.state) {
      case "?":
        throw new Error(`unsupported state: ${status.state}`);
      default:
        return network.fetch_log_diffs(status.source, range);
    }
  },
});
