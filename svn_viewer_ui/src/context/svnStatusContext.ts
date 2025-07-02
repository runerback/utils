import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";
import moment from "moment";

export interface ISvnStatusContext {
  readonly stream$: Observable<SvnStatusStream>;
}

export const SvnStatusContext = createContext<ISvnStatusContext>(null!);

export const provideSvnStatus = async (flush?: boolean) => {
  const result = await network.fetch_status(flush);
  if (!!result) {
    if (!!result.payload) {
      const id = moment().valueOf().toString();
      setTimeout(() => {
        publishSvnStatusStream({
          id,
          job: "FETCH_STATUS",
          status: result.payload ?? [],
          finished: true,
        });
      }, 0);
      return id;
    } else if (!!result.id) {
      return result.id;
    }
  }
};

const stream$ = new Subject<SvnStatusStream>();

export const publishSvnStatusStream = (source: SvnStatusStream) => {
  stream$.next(source);
};

export default (): ISvnStatusContext => ({
  stream$,
});
