from pydantic import BaseModel


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


# end class
