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
