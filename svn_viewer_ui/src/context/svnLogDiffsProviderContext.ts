import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnLogDiffsProviderContext {
  readonly stream$: Observable<SvnLogDiffsProviderStream>;
  provide: (
    status: SvnStatusItem,
    range: FetchLogDiffsRange
  ) => Promise<string | null | undefined>;
}

export const SvnLogDiffsProviderContext =
  createContext<ISvnLogDiffsProviderContext>(null!);

const stream$ = new Subject<SvnLogDiffsProviderStream>();

export const publishSvnLogDiffsStream = (stream: SvnLogDiffsProviderStream) => {
  stream$.next(stream);
};

export default (): ISvnLogDiffsProviderContext => ({
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
