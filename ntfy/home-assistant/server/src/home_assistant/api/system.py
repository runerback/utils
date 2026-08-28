from fastapi import APIRouter

from .. import auth, system

router = APIRouter()


@router.get("/system")
def get_system(current_user: auth.CurrentUser):
    return {
        "cpu_temp": system.cpu_temp_celsius(),
        "memory": system.memory_usage(),
    }
