from fastapi import FastAPI
import logging
import inngest
import inngest.fast_api
import os
from pydantic import BaseModel
import uvicorn
from fastapi_swagger import patch_fastapi
from LoggingMiddleware import LoggingMiddleware

svn_executable = os.environ.get("SVN_EXECUTABLE")
assert os.path.exists(svn_executable)

inngest_dev_port = os.environ.get("INNGEST_DEV_PORT")
assert inngest_dev_port

inngest_client = inngest.Inngest(
    app_id="svn",
    api_base_url=f"http://localhost:{inngest_dev_port}",
    event_key=os.environ['INNGEST_EVENT_KEY'],
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
    settings = ctx.event.data.get("settings")
    if not settings:
        raise ValueError("settings")
    # end if
    svn_root = settings.get("svn_root")
    if not svn_root or not isinstance(svn_root, str):
        raise ValueError("settings.svn_root")
    # end if
    return fetch_settings(svn_root)


# end def

app = FastAPI(docs_url=None, swagger_ui_oauth2_redirect_url=None)


class SettingsModel(BaseModel):
    svn_root: str


# end class


class SettingsRequestModel(BaseModel):
    settings: SettingsModel


# end class


@app.post("/svn/fetch/settings")
async def create_item(data: SettingsRequestModel):
    ids = await inngest_client.send(
        events=inngest.Event(
            name="fetch.settings",
            data={"settings": {"svn_root": data.settings.svn_root}},
        )
    )
    return ids


# end def

if __name__ == "__main__":
    port = int(os.environ.get("INNGEST_PORT", 8000))
    app.add_middleware(LoggingMiddleware, dispatch=None, logger=inngest_client.logger)
    inngest.fast_api.serve(app, inngest_client, [test, fetch_settings])
    patch_fastapi(app)
    uvicorn.run(app, port=port)

# end if
