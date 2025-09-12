import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import type { INotifyContext } from "./notifyContext";
import type { IStatusContext } from "./statusContext";
import network from "./network";

export interface ISvnRevertContext {
  readonly succeed$: Observable<SvnStatusItem>;
  revert: (props: { status: SvnStatusItem }) => void;
}

export const SvnRevertContext = createContext<ISvnRevertContext>(null!);

const succeed$ = new Subject<SvnStatusItem>();

export default (deps: {
  notifier: INotifyContext;
  status: IStatusContext;
}): ISvnRevertContext => ({
  succeed$,
  revert: (props) => {
    if (props.status.state === "M") {
      deps.status.busy();
      network
        .revert(props.status.source)
        .then((result) => {
          if (!result) {
            deps.notifier.notify("Maybe . . .", "info");
          } else {
            if (!!result.error) {
              deps.notifier.notify(result.error, "error");
            } else if (!!result.output) {
              deps.notifier.notify(result.output, "success");
              succeed$.next(props.status);
            } else {
              deps.notifier.notify("done or not", "success");
            }
          }
        })
        .finally(() => deps.status.idle());
    } else {
      deps.notifier.notify("not supported", "warning");
    }
  },
});
