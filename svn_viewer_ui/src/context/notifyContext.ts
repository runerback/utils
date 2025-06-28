import type { NotificationInstance } from "antd/es/notification/interface";
import { createContext } from "preact";

export interface INotifyContext {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}

export const NotifyContext = createContext<INotifyContext>(null!);

export default (
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void
): INotifyContext => ({
  notify,
});
