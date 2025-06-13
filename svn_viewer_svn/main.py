import json
from fastapi import FastAPI, HTTPException
import os
import uvicorn
import logging
from logging.config import dictConfig
from fastapi_swagger import patch_fastapi
from LoggingMiddleware import LoggingMiddleware
from starlette.middleware.cors import CORSMiddleware
from models import (
    SettingsRequestModel,
    SvnDiffRequestModel,
    SvnLogsRequestModel,
    SvnStatusRequestModel,
    SvnUnversionedRequestModel,
)
from svn import fetch_settings, svn_hash
from worker import (
    add_fetch_svn_diff_job,
    add_fetch_svn_logs_job,
    add_fetch_svn_status_job,
    add_fetch_svn_unversioned_job,
    add_send_message_job,
)

with open("logging.config.json", "r") as logging_config:
    dictConfig(json.load(logging_config))
logger = logging.getLogger("uvicorn")


app = FastAPI(docs_url=None, swagger_ui_oauth2_redirect_url=None)


@app.post("/svn/fetch/settings", response_model=str)
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
    add_send_message_job(id, {"data": "settings fetched"})
    return id


# end def


@app.post("/svn/fetch/status", response_model=str)
async def fetch_status(data: SvnStatusRequestModel):
    return add_fetch_svn_status_job(data.job)


# end def


@app.post("/svn/fetch/diff", response_model=str)
async def fetch_diff(data: SvnDiffRequestModel):
    assert data.path
    return add_fetch_svn_diff_job(data.path, data.job)


# end def


@app.post("/svn/fetch/unversioned", response_model=str)
async def fetch_diff(data: SvnUnversionedRequestModel):
    assert data.path
    return add_fetch_svn_unversioned_job(data.path, data.job)


# end def


@app.post("/svn/fetch/logs", response_model=str)
async def fetch_diff(data: SvnLogsRequestModel):
    assert data.path
    return add_fetch_svn_logs_job(data.path, data.job)


# end def

if __name__ == "__main__":
    port = os.environ.get("PORT")
    assert port
    app.add_middleware(LoggingMiddleware, dispatch=None, logger=logger)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    patch_fastapi(app)
    uvicorn.run(app, port=int(port))

# end if
