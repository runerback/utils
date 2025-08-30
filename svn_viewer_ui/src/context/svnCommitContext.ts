import { computed, signal, type ReadonlySignal } from "@preact/signals-react";
import { createContext } from "preact";
import network from "./network";

export interface ISvnCommitContext {
  readonly show$: ReadonlySignal<boolean>;
  readonly files$: ReadonlySignal<string[]>;
  show: () => void;
  close: () => void;
  append: (file: string) => void;
  remove: (file: string) => void;
  clear: () => void;
  commit: (props: { message: string; files: Array<string> }) => Promise<string>;
}

export const SvnCommitContext = createContext<ISvnCommitContext>(null!);

const show$ = signal(false);
const files$ = signal<Record<string, boolean>>({});

export default (): ISvnCommitContext => ({
  show$,
  files$: computed(() => {
    return Object.entries(files$.value)
      .filter(([, checked]) => !!checked)
      .map(([file, _]) => file);
  }),
  show: () => {
    show$.value = true;
  },
  close: () => {
    show$.value = false;
  },
  append: (file) => {
    if (!!file) {
      const next = files$.peek();
      next[file] = true;
      files$.value = { ...next };
    }
  },
  remove: (file) => {
    if (!!file) {
      const next = files$.peek();
      if (next[file]) {
        next[file] = false;
      }
      files$.value = { ...next };
    }
  },
  clear: () => (files$.value = {}),
  commit: (props) => {
    return network.commit(props.message, props.files);
  },
});
