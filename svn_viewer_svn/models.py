from typing import List
from pydantic import BaseModel


class SchedulerStateResponseModel(BaseModel):
    runningJobsCount: int


# end class


class SvnRequestModel(BaseModel):
    job: str | None


# end class


class SettingsRequestModel(SvnRequestModel):
    path: str


# end class


class SvnStatusRequestModel(SvnRequestModel):
    pass


# end class


class SvnDiffRequestModel(SvnRequestModel):
    path: str


# end class


class SvnUnversionedRequestModel(SvnRequestModel):
    path: str


# end class


class SvnFileRemoteRequestModel(SvnRequestModel):
    path: str


# end class


class SvnFileStatusRequestModel(SvnRequestModel):
    path: str


# end class


class SvnLogsRequestModel(SvnRequestModel):
    path: str


# end class


class SvnLogDiffsRequestModel(SvnRequestModel):
    path: str
    n: int
    m: int | None


# end class


class SvnFetchTreeRequestModel(SvnRequestModel):
    path: str


# end class


class SvnFetchInfoRequestModel(SvnRequestModel):
    path: str
    status: bool | None = None


# end class


class SvnFetchRevisionLogsRequestModel(SvnRequestModel):
    path: str
    rev: int


# end class


class SvnOperationResponseModel(BaseModel):
    args: List[str] | None


# end class


class SvnCommitRequestModel(BaseModel):
    message: str
    files: list[str]
    commit: bool | None


# end class


class SvnCommitResponseModel(BaseModel):
    output: str | None
    error: str | None


# end class


class SvnRevertRequestModel(BaseModel):
    file: str


# end class


class SvnRevertResponseModel(BaseModel):
    output: str | None
    error: str | None


# end class
