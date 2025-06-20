import { type ReadonlySignal } from "@preact/signals-react";
import { createContext } from "preact";

export interface ISignalPromiseContext {
  provide: <T>(
    source: ReadonlySignal<T>,
    condition?: (value: T) => boolean
  ) => Promise<T>;
}

export const SignalPromiseContext = createContext<ISignalPromiseContext>(null!);

export default (): ISignalPromiseContext => ({
  provide: <T>(
    source: ReadonlySignal<T>,
    condition?: (value: T) => boolean
  ) => {
    console.log("provide!!!!!!!!!!!!!!!");
    const callbacks = Array<(value: T) => void>();
    const listener = {
      onCompleted: (callback: (value: T) => void) => {
        console.log("on Completed!!!!!!!!!!!!!!!!!!!!");
        callbacks.push(callback);
      },
      completed: (value: T) => {
        console.log("completed!!!!!!!!!!!!!!!!!!!!");
        callbacks.forEach((callback) => callback?.(value));
      },
    };
    const promise = new Promise<T>((resolve) => {
      listener.onCompleted((value) => resolve(value));
    });
    const disposable = source.subscribe((value) => {
      console.log("value changed!!!!!!!!!!!!!!!!!!!!");
      if (!condition || !!condition?.(value)) {
        listener.completed(value);
      } else {
        console.log("cond unmatched!!!!!!!!!!!", value);
      }
    });
    promise.then(() => disposable());
    return promise;
  },
});
