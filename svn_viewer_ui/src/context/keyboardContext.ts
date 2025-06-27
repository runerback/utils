import { signal, type ReadonlySignal } from "@preact/signals-react";
import { createContext } from "preact";

export interface IKeyboardContext {
  readonly ctrl$: ReadonlySignal<boolean>;
  onCtrl(pressing?: boolean): void;
}

export const KeyboardContext = createContext<IKeyboardContext>(null!);

const ctrl$ = signal(false);

export default (): IKeyboardContext => ({
  ctrl$,
  onCtrl: (pressing) => (ctrl$.value = Boolean(pressing)),
});
