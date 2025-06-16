import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnProviderContext {
  readonly stream$: Observable<SvnProviderStream>;
  provide: (status: SvnStatusItem) => Promise<string | null | undefined>;
}

export const SvnProviderContext = createContext<ISvnProviderContext>(
  null!
);

const stream$ = new Subject<SvnProviderStream>();

export const publishSvnStream = (stream: SvnProviderStream) => {
  stream$.next(stream);
};

export default (): ISvnProviderContext => ({
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
