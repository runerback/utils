import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";
import { signal, type ReadonlySignal } from "@preact/signals-react";

export interface ISvnTreeContext {
  readonly stream$: Observable<SvnTreeStream>;
  readonly show$: ReadonlySignal<boolean>;
  provide: (root?: string) => Promise<string | null | undefined>;
  show: () => void;
  close: () => void;
}

export const SvnTreeContext = createContext<ISvnTreeContext>(null!);

const stream$ = new Subject<SvnTreeStream>();

export const publishSvnTreeStream = (source: SvnTreeStream) => {
  stream$.next(source);
};

const show$ = signal(false);

export default (): ISvnTreeContext => ({
  stream$,
  show$,
  provide: (root) => {
    return network.fetch_tree(root);
  },
  show: () => {
    show$.value = true;
  },
  close: () => {
    show$.value = false;
  },
});
