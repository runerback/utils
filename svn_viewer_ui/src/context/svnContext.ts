import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnContext {
  readonly stream$: Observable<SvnStream>;
  provide: (status: SvnStatusItem) => Promise<string | null | undefined>;
}

export const SvnContext = createContext<ISvnContext>(
  null!
);

const stream$ = new Subject<SvnStream>();

export const publishSvnStream = (stream: SvnStream) => {
  stream$.next(stream);
};

export default (): ISvnContext => ({
  stream$,
  provide: (status) => {
    switch (status.state) {
      case "?":
        return network.fetch_unversioned(status.source);
      default:
        return network.fetch_diff(status.source);
    }
  },
});
