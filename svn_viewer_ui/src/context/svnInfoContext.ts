import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";
import { computed, signal, type ReadonlySignal } from "@preact/signals-react";
import moment from "moment";

export interface ISvnInfoContext {
  readonly stream$: Observable<SvnInfoStream>;
  readonly fetchingInfoTaskCount: ReadonlySignal<number>;
  readonly reachMaxFetchInfoTaskCount: ReadonlySignal<boolean>;
  provide: (args: {
    root: string;
    status?: boolean;
    flush?: boolean;
  }) => Promise<string | null | undefined>;
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
  provide: async (args) => {
    console.log("[SvnInfoContext]", args);
    fetchingInfoTaskCount.value = fetchingInfoTaskCount.value + 1;
    const result = await network.fetch_info(args.root, args.status, args.flush);
    if (!!result) {
      if (!!result.payload) {
        const id = moment().valueOf().toString();
        setTimeout(() => {
          publishSvnInfoStream({
            id,
            job: "FETCH_INFO",
            info: result.payload,
            finished: true,
          });
        }, 0);
        return id;
      } else if (!!result.id) {
        return result.id;
      }
    }
  },
  ready: () => {
    fetchingInfoTaskCount.value = Math.max(0, fetchingInfoTaskCount.value - 1);
  },
});
