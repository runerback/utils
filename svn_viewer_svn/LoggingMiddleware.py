from logging import Logger
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware


class LoggingMiddleware(BaseHTTPMiddleware):

    def __init__(self, app, dispatch, logger: Logger | None = None):
        super().__init__(app, dispatch)
        self.logger = logger

    # end def

    async def dispatch(self, request: Request, call_next):
        # Log request details
        client_ip = request.client.host
        method = request.method

        self.logger.info(
            f"Request: {method} {request.url.path}{request.url.query} {{{await request.json()}}} from {client_ip}"
        )

        # Process the request
        response = await call_next(request)

        # Log response details
        status_code = response.status_code
        self.logger.info(
            f"Response: {method} {request.url.path} returned {status_code} to {client_ip}"
        )

        return response

    # end def


# end class
