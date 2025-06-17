import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface ISvnTreeContext {
  readonly stream$: Observable<SvnTreeStream>;
  provide: (root?: string) => Promise<string | null | undefined>;
}

export const SvnTreeContext = createContext<ISvnTreeContext>(null!);

const stream$ = new Subject<SvnTreeStream>();

export const publishSvnTreeStream = (source: SvnTreeStream) => {
  stream$.next(source);
};

export default (): ISvnTreeContext => ({
  stream$,
  provide: (root) => {
    return network.fetch_tree(root);
  },
});
