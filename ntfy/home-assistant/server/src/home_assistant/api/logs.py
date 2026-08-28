from fastapi import APIRouter, HTTPException

from .. import auth, log_reader, settings_store

router = APIRouter()


@router.get("/logs")
def get_logs(current_user: auth.CurrentUser):
    use_journal = settings_store.get_logs_use_journal()
    if use_journal:
        try:
            lines = log_reader.read_journalctl("home-assistant")
        except log_reader.LogReaderError as exc:
            raise HTTPException(status_code=500, detail=str(exc))
    else:
        path = settings_store.get_logs_path()
        try:
            lines = log_reader.read_log_file(path)
        except log_reader.LogReaderError as exc:
            raise HTTPException(status_code=500, detail=str(exc))
    return {"lines": lines}


@router.post("/logs/clear")
def clear_logs(current_user: auth.CurrentUser, csrf: auth.CsrfRequired):
    if settings_store.get_logs_use_journal():
        raise HTTPException(status_code=400, detail="Cannot clear journalctl logs")
    path = settings_store.get_logs_path()
    try:
        log_reader.clear_log_file(path)
    except log_reader.LogReaderError as exc:
        raise HTTPException(status_code=500, detail=str(exc))
    return {"ok": True}
