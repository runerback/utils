from __future__ import annotations

import os

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse

TOKEN_ENV_VAR = "VAE_TOKEN"
TOKEN_HEADER = "x-token"
TOKEN_QUERY_PARAM = "token"


def resolve_auth_token() -> str:
    return os.getenv(TOKEN_ENV_VAR, "").strip()


class TokenAuthMiddleware(BaseHTTPMiddleware):
    """Simple shared-token auth for LAN deployments.

    Disabled when VAE_TOKEN is unset (local development). Accepts the token
    via the X-Token header or a ?token= query parameter (the latter is needed
    for media streaming clients that cannot set headers easily).
    """

    def __init__(self, app, token: str = ""):
        super().__init__(app)
        self._token = token

    async def dispatch(self, request: Request, call_next):
        if not self._token or self._is_exempt(request.url.path):
            return await call_next(request)
        provided = request.headers.get(TOKEN_HEADER) or request.query_params.get(TOKEN_QUERY_PARAM) or ""
        if provided != self._token:
            return JSONResponse(status_code=401, content={"detail": "Invalid or missing token"})
        return await call_next(request)

    @staticmethod
    def _is_exempt(path: str) -> bool:
        # The web shell stays open so the browser UI can load; its /api calls
        # still require the token when one is configured.
        return path == "/" or path == "/health" or path.startswith("/static")
