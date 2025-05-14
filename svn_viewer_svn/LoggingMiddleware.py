from logging import Logger
from fastapi import Request


class LoggingMiddleware:

    def __init__(self, _, logger: Logger | None = None):
        # no one need app here
        self.logger = logger

    # end def

    async def dispatch(self, request: Request, call_next):
        # Log request details
        client_ip = request.client.host
        method = request.method
        url = request.url.path

        self.logger.info(f"Request: {method} {url} from {client_ip}")

        # Process the request
        response = await call_next(request)

        # Log response details
        status_code = response.status_code
        self.logger.info(
            f"Response: {method} {url} returned {status_code} to {client_ip}"
        )

        return response

    # end def


# end class
