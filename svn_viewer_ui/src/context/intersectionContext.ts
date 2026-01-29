import { createContext } from "preact";
import type { MutableRef } from "preact/hooks";

type RegisteredIntersection = {
    readonly ref: MutableRef<HTMLElement>,
    readonly callback: (active: boolean) => void,
}

export interface IIntersectionContext {
    readonly ObserverCallback$: IntersectionObserverCallback;
    register: (id: string, ref: MutableRef<HTMLElement>, callback: (active: boolean) => void) => void;
    unregister: (id: string) => void;
}

export const IntersectionContext = createContext<IIntersectionContext>(null!);

const registers: Record<string, RegisteredIntersection> = {};

const ObserverCallback: IntersectionObserverCallback = (entries) => {
    entries.forEach((entry) => {
        if (entry.target.textContent) {
            const registered = registers[entry.target.textContent];
            if (!!registered) {
                registered.callback(entry.isIntersecting);
            }
        }
    });
};

export default (): IIntersectionContext => ({
    ObserverCallback$: ObserverCallback,
    register(id, ref, callback) {
        registers[id] = { ref, callback };
    },
    unregister(id) {
        delete registers[id];
    },
});