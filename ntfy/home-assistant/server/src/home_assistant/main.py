import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated, Any

from fastapi import FastAPI, Form, HTTPException, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.sessions import SessionMiddleware

from . import auth, config, db, events
from .api import messages, settings, system, topics

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    db.init_db()
    events.subscribe_all_topics()
    events.start_consumer()
    yield
    events.subscriber.unsubscribe_all()
    with events._sse_lock:
        events._sse_clients.clear()


app = FastAPI(lifespan=lifespan)
app.add_middleware(
    SessionMiddleware,
    secret_key=config.SECRET_KEY,
    session_cookie="session",
    max_age=None,
    same_site="lax",
    https_only=False,
)

app.include_router(topics.router, prefix="/api")
app.include_router(messages.router, prefix="/api")
app.include_router(settings.router, prefix="/api")
app.include_router(system.router, prefix="/api")


@app.post("/api/login")
async def login(request: Request, username: Annotated[str, Form()], password: Annotated[str, Form()]):
    username = username.strip()
    if not username or not password:
        raise HTTPException(status_code=400, detail="Username and password are required")
    if not auth.authenticate_system_user(username, password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    auth.login_user(request, username)
    return {"ok": True}


@app.post("/api/logout")
async def logout(request: Request, current_user: auth.CurrentUser, csrf: auth.CsrfRequired):
    auth.logout_user(request)
    return {"ok": True}


@app.get("/api/me")
async def me(current_user: auth.CurrentUser):
    return {"username": current_user}


# Serve built web in production; during development Vite proxies /api.
_dist_path = Path(__file__).parents[3] / "web" / "dist"
if _dist_path.exists():
    app.mount("/", StaticFiles(directory=_dist_path, html=True), name="web")
