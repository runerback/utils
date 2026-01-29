from datetime import datetime
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
    FileModel,
    SchedulerStateResponseModel,
    SettingsRequestModel,
    SvnCommitRequestModel,
    SvnCommitResponseModel,
    SvnDiffRequestModel,
    SvnFetchInfoRequestModel,
    SvnFetchRevisionLogsRequestModel,
    SvnFetchTreeRequestModel,
    SvnFileRemoteRequestModel,
    SvnFileStatusRequestModel,
    SvnLogDiffsRequestModel,
    SvnLogsRequestModel,
    SvnOperationResponseModel,
    SvnRevertRequestModel,
    SvnRevertResponseModel,
    SvnStatusRequestModel,
    SvnUnversionedRequestModel,
)
from svn import (
    get_svn_file_is_file,
    get_svn_file_modifiedtime,
    svn_commit_changes,
    svn_repo_browser,
    svn_revert_file,
)
from worker import (
    create_fetch_svn_diff_task,
    create_fetch_svn_file_remote_task,
    create_fetch_svn_file_status_task,
    create_fetch_svn_info_task,
    create_fetch_svn_log_diffs_task,
    create_fetch_svn_logs_task,
    create_fetch_svn_settings_task,
    create_fetch_svn_status_task,
    create_fetch_svn_tree_task,
    create_fetch_svn_unversioned_task,
    create_fetch_svn_revision_logs_task,
    get_running_jobs_count,
)
from zoneinfo import ZoneInfo

with open("logging.config.json", "r") as logging_config:
    dictConfig(json.load(logging_config))
logger = logging.getLogger("uvicorn")


app = FastAPI(docs_url=None, swagger_ui_oauth2_redirect_url=None)


@app.get("/scheduler/state", response_model=SchedulerStateResponseModel)
def get_scheduler_state():
    return SchedulerStateResponseModel(runningJobsCount=get_running_jobs_count())


# end def


@app.post("/svn/file/stm", response_model=str)
def get_file_modifiedtime(data: FileModel):
    if not data.path:
        return False
    # end if
    return get_svn_file_modifiedtime(data.path)


# end def


@app.post("/svn/file/isfile", response_model=bool)
def get_file_modifiedtime(data: FileModel):
    if not data.path:
        return False
    # end if
    return get_svn_file_is_file(data.path)


# end def


@app.post("/svn/fetch/settings", response_model=str)
def fetch_settings(data: SettingsRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_settings_task(data.path, data.job)


# end def


@app.post("/svn/fetch/status", response_model=str)
def fetch_status(data: SvnStatusRequestModel):
    return create_fetch_svn_status_task(data.job)


# end def


@app.post("/svn/fetch/diff", response_model=str)
def fetch_diff(data: SvnDiffRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_diff_task(data.path, data.job)


# end def


@app.post("/svn/fetch/unversioned", response_model=str)
def fetch_unversioned(data: SvnUnversionedRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_unversioned_task(data.path, data.job)


# end def


@app.post("/svn/fetch/remote/file", response_model=str)
def fetch_file_remote(data: SvnFileRemoteRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_file_remote_task(data.path, data.job)


# end def


@app.post("/svn/fetch/status/file", response_model=str)
def fetch_file_status(data: SvnFileStatusRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_file_status_task(data.path, data.job)


# end def


@app.post("/svn/fetch/logs", response_model=str)
def fetch_logs(data: SvnLogsRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_logs_task(data.path, data.job)


# end def


@app.post("/svn/fetch/logdiffs", response_model=str)
def fetch_log_diffs(data: SvnLogDiffsRequestModel):
    assert data.path, "path is required"
    assert data.n, "n is required"
    return create_fetch_svn_log_diffs_task(data.path, data.n, data.m, data.job)


# end def


@app.post("/svn/fetch/tree", response_model=str)
def fetch_tree(data: SvnFetchTreeRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_tree_task(data.path, data.job)


# end def


@app.post("/svn/fetch/info", response_model=str)
def fetch_info(data: SvnFetchInfoRequestModel):
    assert data.path, "path is required"
    return create_fetch_svn_info_task(data.path, data.status, data.job)


# end def


@app.post("/svn/fetch/rev/logs", response_model=str)
def fetch_revision_logs(data: SvnFetchRevisionLogsRequestModel):
    assert data.path, "path is required"
    assert data.rev and data.rev > 0, "rev is required and should be positive"
    return create_fetch_svn_revision_logs_task(data.path, data.rev, data.job)


# end def


@app.post("/svn/repo/browser", response_model=SvnOperationResponseModel)
def open_repo_browser():
    args = svn_repo_browser()
    return SvnOperationResponseModel(args=args)


# end def


@app.post("/svn/sync/commit", response_model=SvnCommitResponseModel)
def commit_changes(data: SvnCommitRequestModel):
    assert data, "data is required"
    assert data.files and any(data.files), "any file is required"
    if data.commit:
        assert data.message, "message is required"
    # end if
    error, result = svn_commit_changes(data.message, data.files, data.commit, logger)
    return SvnCommitResponseModel(
        output=str(result) if result else None, error=str(error) if error else None
    )


# end def


@app.post("/svn/sync/revert", response_model=SvnRevertResponseModel)
def revert_file(data: SvnRevertRequestModel):
    assert data, "data is required"
    assert data.file, "file is required"
    error, result = svn_revert_file(data.file)
    return SvnRevertResponseModel(
        output=str(result) if result else None, error=str(error) if error else None
    )


# end def

if __name__ == "__main__":
    port = os.environ.get("PORT")
    assert port, "port not configured"
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
