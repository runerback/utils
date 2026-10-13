import { api, createEventSource } from '../api.ts'

export interface Device {
  device_id: string
  ble_mac: string | null
  name: string | null
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

export interface Subscription {
  close(): void
}

function subscribeWithReconnect(
  url: string,
  onMessage: (data: unknown) => void,
): Subscription {
  let es: EventSource | null = null
  let timer: number | null = null
  let closed = false

  const connect = () => {
    if (closed) return
    es = createEventSource(url)
    es.onmessage = (e) => {
      try {
        onMessage(JSON.parse(e.data))
      } catch {
        // ignore malformed lines
      }
    }
    es.onerror = () => {
      es?.close()
      es = null
      if (!closed) {
        timer = window.setTimeout(connect, 3000)
      }
    }
  }
  connect()

  return {
    close() {
      closed = true
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
      es?.close()
      es = null
    },
  }
}

export function subscribeBleScan(
  onDevice: (device: BleDevice) => void,
): Subscription {
  return subscribeWithReconnect('/api/devices/ble-scan', (data) => {
    onDevice(data as BleDevice)
  })
}

export function subscribeDeviceEvents(
  onEvent: (event: DeviceClaimedEvent) => void,
): Subscription {
  return subscribeWithReconnect('/api/messages/stream', (data) => {
    const event = data as { type?: string }
    if (event.type === 'device_claimed') {
      onEvent(event as unknown as DeviceClaimedEvent)
    }
  })
}
