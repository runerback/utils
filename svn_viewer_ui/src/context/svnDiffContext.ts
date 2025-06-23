import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnDiffContext {
  readonly stream$: Observable<SvnDiffStream>;
  provide: (status: SvnStatusItem) => Promise<string | null | undefined>;
}

export const SvnDiffContext = createContext<ISvnDiffContext>(null!);

const stream$ = new Subject<SvnDiffStream>();

export const publishSvnDiffStream = (stream: SvnDiffStream) => {
  stream$.next(stream);
};

export default (): ISvnDiffContext => ({
  stream$,
  provide: (status) => {
    switch (status.state) {
      case "?":
        return network.fetch_unversioned(status.source);
      case "!":
      case "D":
        return network.fetch_file_remote(status.source);
      default:
        return network.fetch_diff(status.source);
    }
  },
});
