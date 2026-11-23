import asyncio
import unittest
from unittest import mock

from starlette.requests import Request

from app.auth import TokenAuthMiddleware, resolve_auth_token


def _make_request(path: str, headers: list[tuple[bytes, bytes]] | None = None, query_string: bytes = b"") -> Request:
    scope = {
        "type": "http",
        "method": "GET",
        "path": path,
        "headers": headers or [],
        "query_string": query_string,
    }
    return Request(scope)


async def _passthrough(_request):
    return "ok"


class TokenAuthMiddlewareTest(unittest.TestCase):
    def _dispatch(self, middleware: TokenAuthMiddleware, request: Request):
        return asyncio.run(middleware.dispatch(request, _passthrough))

    def test_disabled_when_token_unset(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="")
        result = self._dispatch(middleware, _make_request("/api/projects"))
        self.assertEqual(result, "ok")

    def test_exempt_paths_pass_without_token(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="secret")
        for path in ("/", "/health", "/static/app.js"):
            result = self._dispatch(middleware, _make_request(path))
            self.assertEqual(result, "ok", path)

    def test_missing_token_is_rejected(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="secret")
        result = self._dispatch(middleware, _make_request("/api/projects"))
        self.assertEqual(result.status_code, 401)

    def test_wrong_token_is_rejected(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="secret")
        result = self._dispatch(middleware, _make_request("/api/projects", headers=[(b"x-token", b"nope")]))
        self.assertEqual(result.status_code, 401)

    def test_header_token_is_accepted(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="secret")
        result = self._dispatch(middleware, _make_request("/api/projects", headers=[(b"x-token", b"secret")]))
        self.assertEqual(result, "ok")

    def test_query_token_is_accepted(self) -> None:
        middleware = TokenAuthMiddleware(app=lambda scope, receive, send: None, token="secret")
        result = self._dispatch(middleware, _make_request("/work/preview.mp4", query_string=b"token=secret"))
        self.assertEqual(result, "ok")

    def test_resolve_auth_token_reads_env(self) -> None:
        with mock.patch.dict("os.environ", {"VAE_TOKEN": "  abc  "}):
            self.assertEqual(resolve_auth_token(), "abc")


if __name__ == "__main__":
    unittest.main()
