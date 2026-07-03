import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnRevLogsContext {
  readonly stream$: Observable<SvnRevLogsStream>;
  provide: (
    dir: string,
    revision: number
  ) => Promise<string | null | undefined>;
}

export const SvnRevLogsContext = createContext<ISvnRevLogsContext>(null!);

const stream$ = new Subject<SvnRevLogsStream>();

export const publishSvnRevLogsStream = (stream: SvnRevLogsStream) => {
  stream$.next(stream);
};

export default (): ISvnRevLogsContext => ({
  stream$,
  provide: (dir, revision) => {
    return network.fetch_rev_logs(dir, revision);
  },
});
