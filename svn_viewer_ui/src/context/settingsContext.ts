import { createContext } from "preact";
import { debounceTime, filter, map, Subject, type Observable } from "rxjs";
import network from "./network";
import { signal, type ReadonlySignal } from "@preact/signals-react";

export interface ISvnSettingsContext {
  readonly request$: Observable<SettingsRequestStream>;
  readonly stream$: Observable<Settings>;
  readonly current$: ReadonlySignal<Settings | undefined>;
  provide: (
    request: SettingsRequestStream
  ) => Promise<string | null | undefined>;
  pickDir: () => Promise<string | null | undefined>;
}

export const SvnSettingsContext = createContext<ISvnSettingsContext>(null!);

const state: { previous?: SettingsRequest } = {};
const current$ = signal<Settings>();

const request$ = new Subject<SettingsRequest>();
const stream$ = new Subject<Settings>();

export const createRequest = (request: SettingsRequest) => {
  request$.next(request);
};

export const setCurrent = (settings: Settings | undefined) => {
  current$.value = settings;
};

export const onSettingsFetched = (fetched: Settings) => {
  state.previous = {
    svn_root: fetched.svn_root,
    dark_theme: fetched.dark_theme,
  };
  stream$.next({ ...fetched });
};

export default (): ISvnSettingsContext => ({
  request$: request$.pipe(
    debounceTime(200),
    map((it) => ({
      ...it,
      needToSync:
        state.previous?.svn_root !== it.svn_root ||
        state.previous?.dark_theme !== it.dark_theme,
    })),
    filter((it) => !!it.needToSync)
  ),
  stream$,
  current$,
  provide: (request) => {
    return network.update_settings(request);
  },
  pickDir: () => {
    return network.pick_dir(state.previous?.svn_root);
  },
});
