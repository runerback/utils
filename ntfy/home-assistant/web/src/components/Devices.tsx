import { useEffect, useRef, useState } from 'preact/hooks'
import { fetchCsrf } from '../api.ts'
import {
  type BleDevice,
  type Device,
  listDevices,
  pairDevice,
  startBleScan,
  subscribeBleScan,
  subscribeDeviceEvents,
} from '../api/devices.ts'
import { PairingModal } from './PairingModal.tsx'

export function Devices() {
  const [devices, setDevices] = useState<Device[]>([])
  const [loading, setLoading] = useState(false)
  const [scanning, setScanning] = useState(false)
  const [scanDevices, setScanDevices] = useState<BleDevice[]>([])
  const [showScan, setShowScan] = useState(false)
  const [pairingDevice, setPairingDevice] = useState<BleDevice | null>(null)
  const [pairingStatus, setPairingStatus] = useState<'scanning' | 'pairing' | 'success' | 'error'>('scanning')
  const [pairingError, setPairingError] = useState<string | null>(null)

  const [ssid, setSsid] = useState('')
  const [password, setPassword] = useState('')
  const [deviceName, setDeviceName] = useState('')

  const scanEsRef = useRef<EventSource | null>(null)
  const eventsEsRef = useRef<EventSource | null>(null)

  const loadDevices = async () => {
    setLoading(true)
    try {
      setDevices(await listDevices())
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to load devices')
    } finally {
      setLoading(false)
    }
  }

  const startScan = async () => {
    await fetchCsrf()
    setScanDevices([])
    setScanning(true)
    setShowScan(true)
    setPairingStatus('scanning')
    try {
      await startBleScan()
    } catch (e) {
      alert(e instanceof Error ? e.message : 'Failed to start scan')
      setScanning(false)
      return
    }

    if (scanEsRef.current) scanEsRef.current.close()
    const es = subscribeBleScan((device) => {
      setScanDevices((prev) => {
        const filtered = prev.filter((d) => d.address !== device.address)
        return [...filtered, device].sort((a, b) => b.rssi - a.rssi)
      })
    })
    scanEsRef.current = es
  }

  const stopScan = () => {
    if (scanEsRef.current) {
      scanEsRef.current.close()
      scanEsRef.current = null
    }
    setScanning(false)
    setShowScan(false)
    setScanDevices([])
  }

  const beginPair = (device: BleDevice) => {
    setPairingDevice(device)
    setDeviceName(device.name)
    setSsid('')
    setPassword('')
  }

  const submitPair = async (e: Event) => {
    e.preventDefault()
    if (!pairingDevice) return

    setPairingStatus('pairing')
    setPairingError(null)

    try {
      const result = await pairDevice(
        pairingDevice.address,
        deviceName.trim() || pairingDevice.name,
        ssid.trim(),
        password.trim(),
      )
      subscribeToClaimEvents(result.device_id)
    } catch (err) {
      setPairingStatus('error')
      setPairingError(err instanceof Error ? err.message : 'Pairing failed')
    }
  }

  const subscribeToClaimEvents = (deviceId: string) => {
    if (eventsEsRef.current) eventsEsRef.current.close()
    const es = subscribeDeviceEvents((event) => {
      if (event.device_id === deviceId) {
        setPairingStatus('success')
        loadDevices()
        if (eventsEsRef.current) {
          eventsEsRef.current.close()
          eventsEsRef.current = null
        }
      }
    })
    eventsEsRef.current = es
  }

  const closePairing = () => {
    if (eventsEsRef.current) {
      eventsEsRef.current.close()
      eventsEsRef.current = null
    }
    setPairingDevice(null)
    setPairingStatus('scanning')
    setPairingError(null)
  }

  useEffect(() => {
    loadDevices()
    return () => {
      if (scanEsRef.current) scanEsRef.current.close()
      if (eventsEsRef.current) eventsEsRef.current.close()
    }
  }, [])

  const formatDate = (ts: number | null) => {
    if (!ts) return ''
    return new Date(ts * 1000).toLocaleString()
  }

  const statusLabel = (status: string) => {
    switch (status) {
      case 'active':
        return 'Active'
      case 'pending_claim':
        return 'Pending claim'
      case 'pending_status':
        return 'Claimed, waiting for status'
      case 'failed':
        return 'Failed'
      default:
        return status
    }
  }

  return (
    <section>
      <div class="page-header">
        <h1>Devices</h1>
        <button class="btn btn-primary" onClick={startScan} disabled={scanning}>
          {scanning ? 'Scanning…' : '+ Add device'}
        </button>
      </div>

      {showScan && (
        <div class="panel">
          <div class="panel-header">
            <h2>Bluetooth devices</h2>
            <button class="btn btn-small" onClick={stopScan}>Stop</button>
          </div>
          {scanDevices.length === 0 && (
            <p class="muted">{scanning ? 'Looking for ESP32-PROV devices…' : 'No devices found.'}</p>
          )}
          <div class="card-list">
            {scanDevices.map((device) => (
              <div key={device.address} class="card device-scan-item">
                <div class="device-info">
                  <div class="device-name">{device.name}</div>
                  <div class="device-meta">
                    {device.address} · RSSI {device.rssi} dBm
                  </div>
                </div>
                <button class="btn btn-primary btn-small" onClick={() => beginPair(device)}>
                  Pair
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {pairingDevice && (
        <div class="panel">
          <div class="panel-header">
            <h2>Pair {pairingDevice.name}</h2>
          </div>
          <form class="settings-form" onSubmit={submitPair}>
            <label>Device name</label>
            <input
              type="text"
              value={deviceName}
              onInput={(e) => setDeviceName((e.target as HTMLInputElement).value)}
              placeholder={pairingDevice.name}
              required
            />
            <label>Wi-Fi network (SSID)</label>
            <input
              type="text"
              value={ssid}
              onInput={(e) => setSsid((e.target as HTMLInputElement).value)}
              placeholder="Home Wi-Fi"
              required
            />
            <label>Wi-Fi password</label>
            <input
              type="password"
              value={password}
              onInput={(e) => setPassword((e.target as HTMLInputElement).value)}
              placeholder="Password"
              required
            />
            <div class="form-actions">
              <button type="button" class="btn" onClick={() => setPairingDevice(null)}>
                Cancel
              </button>
              <button type="submit" class="btn btn-primary">
                Start pairing
              </button>
            </div>
          </form>
        </div>
      )}

      <div class="card-list">
        {devices.length === 0 && !loading && (
          <p class="muted">No devices registered yet.</p>
        )}
        {devices.map((device) => (
          <div key={device.device_id} class="card device-item">
            <div class="device-info">
              <div class="device-name">
                {device.device_id}
                <span class={`status-pill status-${device.status}`}>
                  {statusLabel(device.status)}
                </span>
              </div>
              <div class="device-meta">
                {device.ble_mac || 'No BLE address'} · Added {formatDate(device.created_at)}
              </div>
            </div>
          </div>
        ))}
      </div>

      <PairingModal
        open={pairingStatus === 'pairing' || pairingStatus === 'success' || pairingStatus === 'error'}
        deviceName={deviceName || pairingDevice?.name || 'Device'}
        status={pairingStatus}
        error={pairingError}
        onClose={closePairing}
      />
    </section>
  )
}
