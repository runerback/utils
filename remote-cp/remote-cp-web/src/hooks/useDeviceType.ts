import { useMemo } from "preact/hooks";
import { detectDeviceType } from "../utils/device";

export function useDeviceType() {
  return useMemo(() => detectDeviceType(), []);
}
