import SignalPromiseContextProvider, {
  SignalPromiseContext,
} from "../context/signalPromiseContext";
import type { NotificationInstance } from "antd/es/notification/interface";
import Settings from "./settings";

export default (props: {
  notify: (
    message: string,
    type: keyof Omit<NotificationInstance, "open" | "destroy">
  ) => void;
}) => {
  const signalPromiseContext = SignalPromiseContextProvider();
  return (
    <SignalPromiseContext.Provider value={signalPromiseContext}>
      <Settings {...props} />
    </SignalPromiseContext.Provider>
  );
};
