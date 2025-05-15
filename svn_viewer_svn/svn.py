import os
import hashlib

svn_executable = os.environ.get("SVN_EXECUTABLE")
assert svn_executable and os.path.exists(svn_executable)

_settings = {"svn_root": "", "svn_root_hash": ""}


def fetch_settings(svn_root: str):
    _settings["svn_root"] = svn_root
    if svn_root:
        md5 = hashlib.md5(svn_root.encode())
        _settings["svn_root_hash"] = md5.hexdigest()
    else:
        _settings["svn_root_hash"] = ""
    # end if
    return _settings["svn_root_hash"]


# end def
