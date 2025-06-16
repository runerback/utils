from pydantic import BaseModel


class SettingsModel(BaseModel):
    svn_root: str


# end class


class SettingsRequestModel(BaseModel):
    settings: SettingsModel


# end class


class SvnRequestModel(BaseModel):
    job: str | None


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


class SvnLogsRequestModel(SvnRequestModel):
    path: str


# end class


class SvnLogDiffsRequestModel(SvnRequestModel):
    path: str
    n: int
    m: int | None


# end class
