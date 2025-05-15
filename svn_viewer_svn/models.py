from pydantic import BaseModel


class SettingsModel(BaseModel):
    svn_root: str


# end class


class SettingsRequestModel(BaseModel):
    settings: SettingsModel


# end class
