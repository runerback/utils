import json
import os
import subprocess
from typing import Any, List, Tuple

_svn_executable = os.environ.get("SVN_EXECUTABLE")
assert _svn_executable and os.path.exists(
    _svn_executable
), "svn executable not find or configured"

_settings = {
    "svn_root": "",
    "svn_repo": "",
    "svn_props": "",
    "svn_rev": "",
}


def validateUrl(source: str | None):
    svn_root = _settings["svn_root"]
    assert svn_root, "svn root is required"
    if not source or len(source) == 0:
        return None
    # end if
    url = "/".join([svn_root, source.replace("\\", "/").strip("/")])
    if not os.path.exists(url):
        return None
    # end if
    return url


# end def


def svn_fetch_settings(svn_root: str):
    _settings["svn_root"] = svn_root
    repo = subprocess.run(
        [_svn_executable, "info", "--show-item", "url", svn_root],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    assert not repo.stderr, str(repo.stderr)
    if repo.stdout:
        _settings["svn_repo"] = repo.stdout.strip("\n")
    # end if
    rev = subprocess.run(
        [_svn_executable, "info", "--show-item", "revision", svn_root],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    assert not rev.stderr, str(rev.stderr)
    if rev.stdout:
        _settings["svn_rev"] = rev.stdout.strip("\n")
    # end if
    props = subprocess.run(
        [_svn_executable, "proplist", "-v", svn_root],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    assert not props.stderr, str(props.stderr)
    if props.stdout:
        _settings["svn_props"] = props.stdout
    print(f"[settings] {_settings}")
    return {
        "svn_root": _settings["svn_root"],
        "svn_repo": _settings["svn_repo"],
        "svn_rev": _settings["svn_rev"],
    }


# end def


def svn_fetch_status() -> Tuple[Any | None, str | None]:
    svn_root = _settings["svn_root"]
    assert svn_root, "svn root is required"
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


def svn_fetch_diff(path: str) -> Tuple[Any | None, str | None]:
    url = validateUrl(path)
    assert url, "invalid path"
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


class svn_fetch_file_status_result:
    def __init__(
        self,
        error: Any | None = None,
        status: str | None = None,
        url: str | None = None,
    ):
        self.error = error
        self.status = status
        self.url = url

    # end def


# end class


def svn_fetch_file_status(path: str) -> svn_fetch_file_status_result:
    url = validateUrl(path)
    assert url, "invalid path"
    print(f'[svn_fetch_file_status] "{url}"')
    if not os.path.isfile(url):
        return svn_fetch_file_status_result(error="this is D.I.R, how copy? over!")
    # end if
    status = subprocess.run(
        [_svn_executable, "status", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    if status.stderr:
        return svn_fetch_file_status_result(error=status.stderr)
    # end if

    if not status.stdout:
        return svn_fetch_file_status_result()
    # end if

    return svn_fetch_file_status_result(status=status.stdout[0], url=url)


# end def


def svn_unversioned(path: str) -> Tuple[Any | None, str | None]:
    status = svn_fetch_file_status(path)
    if status.error:
        return status.error, None
    # end if
    if not status.status or status.status != "?":
        return "not ✌️unversioned✌️", None
    # end if
    assert status.url, "invalid path"
    print(f'[svn_fetch_file_unversioned] "{status.url}"')
    with open(status.url, mode="r") as file:
        return None, file.read()
    # end with


# end def


def svn_file_remote(path: str) -> Tuple[Any | None, str | None]:
    assert path, "path is required"
    repo = _settings["svn_repo"]
    assert repo, "repo not configured"
    url = "/".join([repo, path.replace("\\", "/").strip("/")])
    print(f'[svn_fetch_file_remote] "{url}"')
    cat = subprocess.run(
        [_svn_executable, "cat", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return cat.stderr, cat.stdout


# end def


def svn_fetch_logs(path: str) -> Tuple[Any | None, str | None]:
    url = validateUrl(path)
    assert url, "invalid path"
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


def svn_fetch_log_diffs(
    path: str, n: int, m: int | None
) -> Tuple[Any | None, str | None]:
    url = validateUrl(path)
    assert url, "invalid path"
    assert n and n >= 0, "invalid revision range start"
    assert not m or m > 0, "invalid revistion range end"
    range = f"{n}:{m}" if m > 0 else str(n)
    print(f'[fetch_svn_log_diffs] "{url}" with [{range}]')
    logs = subprocess.run(
        [_svn_executable, "diff", "-r", range, url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return logs.stderr, logs.stdout


# end def


class svn_fetch_file_tree_result_node:
    def __init__(self, name: str, dir: bool = False, children: bool = False):
        self.name = name
        self.dir = dir
        self.children = children

    # end def


# end class


class svn_fetch_file_tree_result:
    def __init__(
        self,
        nodes: List[svn_fetch_file_tree_result_node] | None = None,
        props: str | None = None,
        error: Any | None = None,
    ):
        self.nodes = nodes
        self.props = props
        self.error = error

    # end def

    def toJSON(self):
        props = ('"props": ' + json.dumps(self.props)) if self.props else ""
        if not self.nodes:
            return "{" + props + "}"
        # end if
        return (
            "{"
            + ((props + ",") if self.props else "")
            + '"nodes":'
            + "["
            + ",".join(
                [json.dumps(x, default=lambda it: it.__dict__) for x in self.nodes]
            )
            + "]}"
        )

    # end def


# end class


def svn_fetch_file_tree(path: str) -> svn_fetch_file_tree_result:
    url = validateUrl(path)
    assert url, "invalid path"
    print(f'[svn_fetch_file_tree] "{url}"')
    if not os.path.isdir(url):
        return svn_fetch_file_tree_result(error="man need D.I.R")
    # end if
    nodes = [
        (
            svn_fetch_file_tree_result_node(
                name=x, dir=True, children=any(os.scandir(f"{url}/{x}"))
            )
            if os.path.isdir(f"{url}/{x}")
            else svn_fetch_file_tree_result_node(name=x)
        )
        for x in os.listdir(url)
    ]
    props = subprocess.run(
        [_svn_executable, "proplist", "-v", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return svn_fetch_file_tree_result(
        nodes=nodes,
        props=(
            _settings["svn_props"]
            + (props.stdout if (props.stdout and not props.stderr) else "")
        ),
    )


# end def


def svn_fetch_info(path: str) -> Tuple[Any | None, str | None]:
    url = validateUrl(path)
    assert url, "invalid path"
    print(f'[fetch_svn_info] "{url}" with [{range}]')
    info = subprocess.run(
        [_svn_executable, "info", url],
        capture_output=True,
        text=True,
        encoding="utf-8",
        shell=True,
    )
    return info.stderr, info.stdout


# end def
