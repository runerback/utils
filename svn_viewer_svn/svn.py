import os
import hashlib
import subprocess

_svn_executable = os.environ.get("SVN_EXECUTABLE")
assert _svn_executable and os.path.exists(_svn_executable)

_settings = {"svn_root": "", "svn_root_hash": ""}


def validateUrl(source: str | None):
    svn_root = _settings["svn_root"]
    assert svn_root
    if not source or len(source) == 0:
        return None
    # end if
    url = os.path.join(svn_root, source).replace("\\", "/")
    if not os.path.exists(url):
        return None
    # end if
    return url


# end def


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
        shell=True,
    )
    return status.stderr, status.stdout


# end def


def svn_fetch_diff(path: str) -> str | None:
    url = validateUrl(path)
    assert url
    print(f'[fetch_svn_diff] "{url}"')
    diff = subprocess.run(
        [_svn_executable, "diff", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return diff.stderr, diff.stdout


# end def


def svn_unversioned(path: str) -> str | None:
    url = validateUrl(path)
    assert url
    print(f'[fetch_svn_unversioned] "{url}"')
    if not os.path.isfile(url):
        return "this is D.I.R, how copy? over!", None
    # end if
    status = subprocess.run(
        [_svn_executable, "status", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    if status.stderr:
        return status.stderr, None
    # end if
    if not status.stdout or status.stdout[0] != "?":
        return "not ✌️unversioned✌️", None
    # end if
    with open(url, mode="r") as file:
        return None, file.read()
    # end with


# end def


def svn_fetch_logs(path: str) -> str | None:
    url = validateUrl(path)
    assert url
    print(f'[fetch_svn_logs] "{url}"')
    logs = subprocess.run(
        [_svn_executable, "log", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return logs.stderr, logs.stdout


# end def
