from fastapi import FastAPI, HTTPException
import logging
import os
import uvicorn
from fastapi_swagger import patch_fastapi
from LoggingMiddleware import LoggingMiddleware
from models import SettingsRequestModel
from svn import fetch_settings
from messages import send_message

app = FastAPI(docs_url=None, swagger_ui_oauth2_redirect_url=None)
logger = logging.getLogger("uvicorn")


@app.post("/svn/fetch/settings")
async def create_item(data: SettingsRequestModel):
    settings = data.settings
    if not settings:
        raise HTTPException(status_code=400, detail="settings required")
    # end if
    svn_root = settings.svn_root
    if not svn_root or not isinstance(svn_root, str):
        raise HTTPException(status_code=400, detail="settings.svn_root required")
    # end if
    id = fetch_settings(svn_root)
    send_message(id, {"data": "settings fetched"})
    return id


# end def

if __name__ == "__main__":
    port = os.environ.get("PORT")
    assert port
    app.add_middleware(LoggingMiddleware, dispatch=None, logger=logger)
    patch_fastapi(app)
    uvicorn.run(app, port=int(port), log_level="info")

# end if
