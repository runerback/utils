"""Security helpers for device provisioning.

Adapted from devices/server/security.py.
"""
import hashlib
import hmac
import secrets

TOKEN_BYTES = 32
CLAIM_KEY_BYTES = 16
SALT_BYTES = 16
MQTT_PASS_BYTES = 18


def generate_device_id() -> str:
    """Device ID in the form esp32-a1b2c3d4."""
    return "esp32-" + secrets.token_hex(4)


def generate_access_token() -> str:
    return secrets.token_urlsafe(TOKEN_BYTES)


def generate_claim_key() -> str:
    return secrets.token_urlsafe(CLAIM_KEY_BYTES)


def generate_mqtt_password() -> str:
    return secrets.token_urlsafe(MQTT_PASS_BYTES)


def hash_secret(secret: str, salt: str | None = None) -> tuple[str, str]:
    """Return (salt, digest), where digest = hex(sha256(salt + secret))."""
    if salt is None:
        salt = secrets.token_hex(SALT_BYTES)
    digest = hashlib.sha256((salt + secret).encode("utf-8")).hexdigest()
    return salt, digest


def verify_secret(secret: str, salt: str, expected_digest: str) -> bool:
    """Constant-time comparison."""
    _, digest = hash_secret(secret, salt)
    return hmac.compare_digest(digest, expected_digest)
