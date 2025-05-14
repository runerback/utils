from fastapi import FastAPI
import logging
import inngest
import inngest.fast_api
import os
import uvicorn

inngest_client = inngest.Inngest(
    app_id="svn",
    api_base_url=f"http://localhost:{os.environ.get("INNGEST_DEV_PORT")}",
    logger=logging.getLogger("uvicorn"),
)


@inngest_client.create_function(
    fn_id="hello-world",
    # Event that triggers this function
    trigger=inngest.TriggerEvent(event="svn/hello.world"),
)
async def my_function(ctx: inngest.Context, step: inngest.Step) -> str:
    ctx.logger.info(ctx.event)
    return "done"


# end def

if __name__ == "__main__":
    port = int(os.environ.get("INNGEST_PORT", 8000))
    app = FastAPI()
    inngest.fast_api.serve(app, inngest_client, [my_function])
    uvicorn.run(app, port=port)

# end if
