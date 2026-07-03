import { signal, type ReadonlySignal } from "@preact/signals-react";
import { createContext } from "preact";

export interface IStatusContext {
  readonly busy$: ReadonlySignal<boolean>;
  busy: () => void;
  idle: () => void;
}

export const StatusContext = createContext<IStatusContext>(null!);

const busy$ = signal(false);

export default (): IStatusContext => ({
  busy$,
  busy: () => {
    busy$.value = true;
  },
  idle: () => {
    busy$.value = false;
  },
});
