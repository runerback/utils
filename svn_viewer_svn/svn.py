import os
import hashlib
import subprocess
from typing import Callable

_svn_executable = os.environ.get("SVN_EXECUTABLE")
assert _svn_executable and os.path.exists(_svn_executable)

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


def svn_hash():
    svn_root_hash = _settings["svn_root_hash"]
    assert svn_root_hash
    return svn_root_hash


# end def


def svn_fetch_status() -> str | None:
    svn_root = _settings["svn_root"]
    assert svn_root
    print(f'[svn_fetch_status] "{svn_root}"')
    status = subprocess.run(
        [_svn_executable, "status", svn_root],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True
    )
    return status.stderr, status.stdout


# end def
