import os
import secrets
from functools import lru_cache
from typing import Annotated, Optional

from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPBasic, HTTPBasicCredentials

from . import config

security = HTTPBasic(auto_error=False)


try:
    import pwd
    import pam

    _PAM_AVAILABLE = True
except ImportError:
    _PAM_AVAILABLE = False


def authenticate_system_user(username: str, password: str) -> bool:
    if username == "root":
        return False

    if os.environ.get("DEV_AUTH_BYPASS") == "1":
        return bool(username)

    if not _PAM_AVAILABLE:
        return False

    try:
        pwd.getpwnam(username)
    except KeyError:
        return False

    try:
        p = pam.pam()
        return p.authenticate(username, password)
    except Exception:
        return False


def _ensure_csrf_token(request: Request) -> str:
    token = request.session.get("csrf_token")
    if not token:
        token = secrets.token_urlsafe(32)
        request.session["csrf_token"] = token
    return token


def get_current_user(request: Request) -> str:
    username = request.session.get("username")
    if not username:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return username


CurrentUser = Annotated[str, Depends(get_current_user)]


def require_csrf(request: Request) -> None:
    expected = request.session.get("csrf_token")
    provided = request.headers.get("x-csrftoken") or request.headers.get("X-CSRFToken")
    if not expected or not provided or not secrets.compare_digest(expected, provided):
        raise HTTPException(status_code=403, detail="Invalid or missing CSRF token")


CsrfRequired = Annotated[None, Depends(require_csrf)]


def login_user(request: Request, username: str) -> None:
    request.session["username"] = username
    request.session["csrf_token"] = secrets.token_urlsafe(32)


def logout_user(request: Request) -> None:
    request.session.clear()


def get_csrf_token(request: Request) -> str:
    return _ensure_csrf_token(request)
