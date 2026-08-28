from fastapi import APIRouter, HTTPException, Request

from .. import auth, settings_store

router = APIRouter()


@router.get("/settings")
def get_settings(current_user: auth.CurrentUser):
    return settings_store.as_dict()


@router.post("/settings")
async def save_settings(
    request: Request,
    current_user: auth.CurrentUser,
    csrf: auth.CsrfRequired,
):
    form = await request.form()
    data = {key: str(value) for key, value in form.multi_items()}
    settings_store.update_from_form(data)
    from .. import events

    events.subscriber.unsubscribe_all()
    events.subscribe_all_topics()
    return {"ok": True}


@router.get("/csrf")
def get_csrf(request: Request, current_user: auth.CurrentUser):
    return {"csrf_token": auth.get_csrf_token(request)}
