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
    const callbacks = Array<(value: T) => void>();
    const listener = {
      onCompleted: (callback: (value: T) => void) => {
        callbacks.push(callback);
      },
      completed: (value: T) => {
        callbacks.forEach((callback) => callback?.(value));
      },
    };
    const promise = new Promise<T>((resolve) => {
      console.log("give an EMPTY PROMISY !!!!!!!!!!!!!!!!!");
      listener.onCompleted((value) => resolve(value));
    });
    const disposable = source.subscribe((value) => {
      if (!condition || !!condition?.(value)) {
        console.log("Condition fetched !!!!!!!!!!!!!!!!!");
        listener.completed(value);
      }
    });
    promise.then(() => disposable());
    return promise;
  },
});
