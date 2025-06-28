import { useMemo } from "preact/hooks";
import StatusContextProvider, { StatusContext } from "../context/statusContext";
import Signals from "./signals";

export default () => {
  const statusContext = useMemo(() => StatusContextProvider(), []);
  return (
    <StatusContext.Provider value={statusContext}>
      <Signals />
    </StatusContext.Provider>
  );
};
