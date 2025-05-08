import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnDiffProviderContext {
  readonly stream$: Observable<SvnDiffProviderStream>;
  provide: (status: SvnStatusItem) => Promise<string | null | undefined>;
}

export const SvnDiffProviderContext = createContext<ISvnDiffProviderContext>(
  null!
);

const svnDiffProviderStream$ = new Subject<SvnDiffProviderStream>();

export const publishSvnDiffStream = (stream: SvnDiffProviderStream) => {
  svnDiffProviderStream$.next(stream);
};

export default (): ISvnDiffProviderContext => ({
  stream$: svnDiffProviderStream$,
  provide: (status) => {
    switch (status.state) {
      case "?":
        return network.fetch_unversioned(status.source);
      default:
        return network.fetch_diff(status.source);
    }
  },
});
