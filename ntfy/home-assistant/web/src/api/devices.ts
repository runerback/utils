import { api, createEventSource } from '../api.ts'

export interface Device {
  device_id: string
  ble_mac: string | null
  status: 'pending_claim' | 'pending_status' | 'active' | 'failed'
  created_at: number
  claimed_at: number | null
}

export interface BleDevice {
  address: string
  name: string
  rssi: number
}

export interface PairingResult {
  device_id: string
  name: string
  status: string
  qr_payload: string
}

export interface DeviceClaimedEvent {
  type: 'device_claimed'
  device_id: string
}

export async function listDevices(): Promise<Device[]> {
  const res = await api('/api/devices')
  return res.json()
}

export async function startBleScan(): Promise<void> {
  const res = await api('/api/devices/ble-scan/start', { method: 'POST' })
  if (!res.ok) {
    const data = await res.json().catch(() => ({}))
    throw new Error(data.detail || 'Failed to start BLE scan')
  }
}

export async function pairDevice(
  bleAddress: string,
  name: string,
  ssid: string,
  password: string,
): Promise<PairingResult> {
  const res = await api('/api/devices/pair', {
    method: 'POST',
    body: new URLSearchParams({
      ble_address: bleAddress,
      name,
      ssid,
      password,
    }),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  const data = await res.json()
  if (!res.ok) {
    throw new Error(data.detail || 'Failed to start pairing')
  }
  return data
}

export function subscribeBleScan(
  onDevice: (device: BleDevice) => void,
): EventSource {
  const es = createEventSource('/api/devices/ble-scan')
  es.onmessage = (e) => {
    try {
      onDevice(JSON.parse(e.data))
    } catch {
      // ignore malformed lines
    }
  }
  es.onerror = () => {
    es.close()
  }
  return es
}

export function subscribeDeviceEvents(
  onEvent: (event: DeviceClaimedEvent) => void,
): EventSource {
  const es = createEventSource('/api/messages/stream')
  es.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'device_claimed') {
        onEvent(data as DeviceClaimedEvent)
      }
    } catch {
      // ignore malformed lines
    }
  }
  es.onerror = () => {
    es.close()
  }
  return es
}
