import { createContext } from "preact";
import { Subject, type Observable } from "rxjs";
import network from "./network";

export interface IMessageContext {
  readonly stream$: Observable<MessageStream>;
}

export const MessageContext = createContext<IMessageContext>(null!);

const stream$ = new Subject<MessageStream>();

network.messages$.subscribe((e: Message) => {
  if (!!e.content) {
    const content = JSON.parse(e.content) as MessageContent;
    if (!!content?.timestamp) {
      stream$.next({ id: e.id, content });
    }
  }
});

export default (): IMessageContext => ({
  stream$,
});
