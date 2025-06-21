import json
from fastapi import FastAPI
import os
import uvicorn
import logging
from logging.config import dictConfig
from fastapi_swagger import patch_fastapi
from LoggingMiddleware import LoggingMiddleware
from starlette.middleware.cors import CORSMiddleware
from models import (
    SchedulerStateResponseModel,
    SettingsRequestModel,
    SvnDiffRequestModel,
    SvnFetchInfoRequestModel,
    SvnFetchTreeRequestModel,
    SvnFileRemoteRequestModel,
    SvnFileStatusRequestModel,
    SvnLogDiffsRequestModel,
    SvnLogsRequestModel,
    SvnStatusRequestModel,
    SvnUnversionedRequestModel,
)
from worker import (
    add_fetch_svn_diff_job,
    add_fetch_svn_file_remote_job,
    add_fetch_svn_file_status_job,
    add_fetch_svn_info_job,
    add_fetch_svn_log_diffs_job,
    add_fetch_svn_logs_job,
    add_fetch_svn_settings_job,
    add_fetch_svn_status_job,
    add_fetch_svn_tree_job,
    add_fetch_svn_unversioned_job,
    get_running_jobs_count,
)

with open("logging.config.json", "r") as logging_config:
    dictConfig(json.load(logging_config))
logger = logging.getLogger("uvicorn")


app = FastAPI(docs_url=None, swagger_ui_oauth2_redirect_url=None)


@app.get("/scheduler/state", response_model=SchedulerStateResponseModel)
def get_scheduler_state():
    return SchedulerStateResponseModel(runningJobsCount=get_running_jobs_count())


# end def


@app.post("/svn/fetch/settings", response_model=str)
def fetch_settings(data: SettingsRequestModel):
    assert data.path
    id = add_fetch_svn_settings_job(data.path, data.job)
    return id


# end def


@app.post("/svn/fetch/status", response_model=str)
def fetch_status(data: SvnStatusRequestModel):
    return add_fetch_svn_status_job(data.job)


# end def


@app.post("/svn/fetch/diff", response_model=str)
def fetch_diff(data: SvnDiffRequestModel):
    assert data.path
    return add_fetch_svn_diff_job(data.path, data.job)


# end def


@app.post("/svn/fetch/unversioned", response_model=str)
def fetch_unversioned(data: SvnUnversionedRequestModel):
    assert data.path
    return add_fetch_svn_unversioned_job(data.path, data.job)


# end def


@app.post("/svn/fetch/remote/file", response_model=str)
def fetch_file_remote(data: SvnFileRemoteRequestModel):
    assert data.path
    return add_fetch_svn_file_remote_job(data.path, data.job)


# end def


@app.post("/svn/fetch/status/file", response_model=str)
def fetch_file_status(data: SvnFileStatusRequestModel):
    assert data.path
    return add_fetch_svn_file_status_job(data.path, data.job)


# end def


@app.post("/svn/fetch/logs", response_model=str)
def fetch_logs(data: SvnLogsRequestModel):
    assert data.path
    return add_fetch_svn_logs_job(data.path, data.job)


# end def


@app.post("/svn/fetch/logdiffs", response_model=str)
def fetch_log_diffs(data: SvnLogDiffsRequestModel):
    assert data.path
    assert data.n
    return add_fetch_svn_log_diffs_job(data.path, data.n, data.m, data.job)


# end def


@app.post("/svn/fetch/tree", response_model=str)
def fetch_tree(data: SvnFetchTreeRequestModel):
    assert data.path
    return add_fetch_svn_tree_job(data.path, data.job)


# end def


@app.post("/svn/fetch/info", response_model=str)
def fetch_info(data: SvnFetchInfoRequestModel):
    assert data.path
    return add_fetch_svn_info_job(data.path, data.job)


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
