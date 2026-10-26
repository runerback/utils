from typing import Annotated

from fastapi import APIRouter, Form, HTTPException

from .. import auth, system

router = APIRouter()


@router.get("/system")
def get_system(current_user: auth.CurrentUser):
    return {
        "cpu_temp": system.cpu_temp_celsius(),
        "memory": system.memory_usage(),
    }


@router.get("/system/bluetooth")
def get_bluetooth(current_user: auth.CurrentUser):
    return system.bluetooth_status()


@router.post("/system/bluetooth/power")
def set_bluetooth_power(
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
    on: Annotated[str, Form()],
):
    try:
        return system.set_bluetooth_power(on.lower() in ("true", "1", "on"))
    except RuntimeError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
