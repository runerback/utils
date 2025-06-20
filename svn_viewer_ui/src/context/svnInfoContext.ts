import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnInfoContext {
  readonly stream$: Observable<SvnInfoStream>;
  provide: (root: string) => Promise<string | null | undefined>;
}

export const SvnInfoContext = createContext<ISvnInfoContext>(null!);

const stream$ = new Subject<SvnInfoStream>();

export const publishSvnInfoStream = (source: SvnInfoStream) => {
  stream$.next(source);
};

export default (): ISvnInfoContext => ({
  stream$,
  provide: (root) => {
    return network.fetch_info(root);
  },
});
