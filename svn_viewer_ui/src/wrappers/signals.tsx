import SignalPromiseContextProvider, {
  SignalPromiseContext,
} from "../context/signalPromiseContext";
import Settings from "./settings";

export default () => {
  const signalPromiseContext = SignalPromiseContextProvider();
  return (
    <SignalPromiseContext.Provider value={signalPromiseContext}>
      <Settings />
    </SignalPromiseContext.Provider>
  );
};
