from fastapi import FastAPI
import logging
import inngest
import inngest.fast_api
import os
import uvicorn

from LoggingMiddleware import LoggingMiddleware

svn_executable = os.environ.get("SVN_EXECUTABLE")
assert os.path.exists(svn_executable)

inngest_dev_port = os.environ.get("INNGEST_DEV_PORT")
assert inngest_dev_port

inngest_client = inngest.Inngest(
    app_id="svn",
    api_base_url=f"http://localhost:{inngest_dev_port}",
    logger=logging.getLogger("uvicorn"),
)


@inngest_client.create_function(
    fn_id="test",
    trigger=inngest.TriggerEvent(event="svn/test"),
)
async def test(ctx: inngest.Context, step: inngest.Step) -> str:
    ctx.logger.info(ctx.event)
    return "done"


@inngest_client.create_function(
    fn_id="fetch-settings",
    trigger=inngest.TriggerEvent(event="svn/fetch.settings"),
)
async def fetch_settings(ctx: inngest.Context, step: inngest.Step) -> str:
    ctx.logger.info(ctx.event)
    return "done"


# end def

if __name__ == "__main__":
    port = int(os.environ.get("INNGEST_PORT", 8000))
    app = FastAPI()
    app.add_middleware(LoggingMiddleware, logger=inngest_client.logger)
    inngest.fast_api.serve(app, inngest_client, [test, fetch_settings])
    uvicorn.run(app, port=port)

# end if
