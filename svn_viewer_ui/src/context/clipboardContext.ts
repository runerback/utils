import { createContext } from "preact";

export interface IClipboardContext {
    readonly available: boolean;
    copy: (content: string) => void;
}

export const ClipboardContext = createContext<IClipboardContext>(null!);

const available = !!navigator.clipboard;

export default (copied: () => void): IClipboardContext => ({
    available,
    copy(content) {
        navigator.clipboard.writeText(content).then(() => copied());
    },
});