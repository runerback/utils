interface PairingModalProps {
  open: boolean
  deviceName: string
  status: 'scanning' | 'pairing' | 'success' | 'error'
  error?: string | null
  onClose: () => void
}

export function PairingModal({
  open,
  deviceName,
  status,
  error,
  onClose,
}: PairingModalProps) {
  if (!open) return null

  const progressLabel =
    status === 'scanning'
      ? 'Scanning for devices…'
      : status === 'pairing'
        ? 'Pairing in progress…'
        : status === 'success'
          ? 'Device paired successfully'
          : 'Pairing failed'

  return (
    <div class="modal-overlay" onClick={onClose}>
      <div class="modal-content" onClick={(e) => e.stopPropagation()}>
        <div class="modal-header">
          <h2>Pair {deviceName}</h2>
          <button class="btn btn-small" onClick={onClose}>
            Close
          </button>
        </div>

        <div class="modal-body">
          <p class="muted">{progressLabel}</p>

          <div class="progress-bar">
            <div
              class={`progress-fill ${status === 'error' ? 'error' : status === 'success' ? 'success' : ''}`}
              style={{ width: status === 'success' ? '100%' : status === 'error' ? '100%' : '60%' }}
            />
          </div>

          {status === 'pairing' && (
            <p>
              A QR code is displayed on the MSU screen. Point the ESP32-CAM at
              the screen and wait for the device to connect.
            </p>
          )}

          {status === 'success' && (
            <p class="success-text">
              The device claimed its credentials and is now active.
            </p>
          )}

          {status === 'error' && error && (
            <p class="error-text">{error}</p>
          )}
        </div>
      </div>
    </div>
  )
}
