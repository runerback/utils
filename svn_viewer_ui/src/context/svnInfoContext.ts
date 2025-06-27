import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";
import { computed, signal, type ReadonlySignal } from "@preact/signals-react";

export interface ISvnInfoContext {
  readonly stream$: Observable<SvnInfoStream>;
  readonly fetchingInfoTaskCount: ReadonlySignal<number>;
  readonly reachMaxFetchInfoTaskCount: ReadonlySignal<boolean>;
  provide: (
    root: string,
    status?: boolean
  ) => Promise<NetworkResponse<SvnTreeNodeInfo> | null | undefined>;
  ready: () => void;
}

export const SvnInfoContext = createContext<ISvnInfoContext>(null!);

const stream$ = new Subject<SvnInfoStream>();

export const publishSvnInfoStream = (source: SvnInfoStream) => {
  stream$.next(source);
};

const maxFetchInfoTaskCount = window.CLIENT_TASK_PARALLEL ?? 2;
if (maxFetchInfoTaskCount < 1) {
  console.warn(`wrong env('CLIENT_TASK_PARALLEL')`);
} else {
  console.log({ CLIENT_TASK_PARALLEL: window.CLIENT_TASK_PARALLEL });
}
const fetchingInfoTaskCount = signal(0);
const reachMaxFetchInfoTaskCount = computed(
  () => fetchingInfoTaskCount.value >= maxFetchInfoTaskCount
);

export default (): ISvnInfoContext => ({
  stream$,
  fetchingInfoTaskCount,
  reachMaxFetchInfoTaskCount,
  provide: (root, status) => {
    fetchingInfoTaskCount.value = fetchingInfoTaskCount.value + 1;
    return network.fetch_info(root, status);
  },
  ready: () => {
    fetchingInfoTaskCount.value = Math.max(0, fetchingInfoTaskCount.value - 1);
  },
});
