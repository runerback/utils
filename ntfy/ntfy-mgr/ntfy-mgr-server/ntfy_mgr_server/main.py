from typing import List, Optional

import requests
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from ntfy_mgr_server import cli, config
from ntfy_mgr_server.auth import create_token, get_current_username

app = FastAPI(title="ntfy Manager Server", version="1.0.0")

if config.CORS_ORIGINS:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=config.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )


class LoginRequest(BaseModel):
    username: str
    password: str


class LoginResponse(BaseModel):
    token: str


class AccessItem(BaseModel):
    topic: str
    permission: str
    provisioned: bool = False


class TokenItem(BaseModel):
    value: str
    label: Optional[str]
    expires: str
    last_origin: str
    last_access: str
    provisioned: bool = False


class UserItem(BaseModel):
    name: str
    role: str
    tier: str
    provisioned: bool = False
    accesses: List[AccessItem] = []
    tokens: List[TokenItem] = []


class UserCreateRequest(BaseModel):
    username: str
    password: str


class AccessRequest(BaseModel):
    topic: str
    permission: str = "read-write"


class TopicAccessRequest(BaseModel):
    username: str
    permission: str = "read-write"


class TokenCreateRequest(BaseModel):
    expires: str = ""
    label: str = ""


class TopicItem(BaseModel):
    name: str
    accessors: List[dict]


def _ntfy_error_response(exc: cli.NtfyError) -> HTTPException:
    return HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))


def validate_admin(username: str, password: str) -> bool:
    try:
        resp = requests.get(
            f"{config.NTFY_BASE_URL}/v1/account",
            auth=(username, password),
            timeout=10,
        )
    except requests.RequestException:
        return False
    if resp.status_code != 200:
        return False
    data = resp.json()
    return data.get("role") == "admin"


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/auth/login", response_model=LoginResponse)
def login(body: LoginRequest) -> LoginResponse:
    if not validate_admin(body.username, body.password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid credentials or not an admin",
        )
    return LoginResponse(token=create_token(body.username))


@app.post("/auth/logout")
def logout(username: str = Depends(get_current_username)) -> None:
    # JWT tokens cannot be invalidated server-side without a denylist.
    # Clients must discard the token.
    return


@app.get("/users", response_model=List[UserItem])
def list_users(username: str = Depends(get_current_username)) -> List[UserItem]:
    users = cli.list_users()
    tokens_map = {ut.user: ut.tokens for ut in cli.list_tokens()}
    return [
        UserItem(
            name=user.name,
            role=user.role,
            tier=user.tier,
            provisioned=user.provisioned,
            accesses=[
                AccessItem(
                    topic=a.topic,
                    permission=a.permission,
                    provisioned=a.provisioned,
                )
                for a in user.accesses
            ],
            tokens=[
                TokenItem(
                    value=t.value,
                    label=t.label,
                    expires=t.expires,
                    last_origin=t.last_origin,
                    last_access=t.last_access,
                    provisioned=t.provisioned,
                )
                for t in tokens_map.get(user.name, [])
            ],
        )
        for user in users
        if user.role == "user"
    ]


@app.post("/users", status_code=status.HTTP_201_CREATED)
def create_user(
    body: UserCreateRequest,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.add_user(body.username, body.password)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"User {body.username} created"}


@app.delete("/users/{name}")
def delete_user(
    name: str,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.remove_user(name)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"User {name} deleted"}


@app.post("/users/{name}/access")
def grant_user_access(
    name: str,
    body: AccessRequest,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.grant_access(name, body.topic, body.permission)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Access granted for {name} on {body.topic}"}


@app.delete("/users/{name}/access/{topic}")
def revoke_user_access(
    name: str,
    topic: str,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.revoke_access(name, topic)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Access revoked for {name} on {topic}"}


@app.post("/users/{name}/tokens")
def create_user_token(
    name: str,
    body: TokenCreateRequest,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.add_token(name, body.expires, body.label)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Token created for {name}"}


@app.delete("/users/{name}/tokens/{token}")
def delete_user_token(
    name: str,
    token: str,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.remove_token(name, token)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": "Token deleted"}


@app.get("/topics", response_model=List[TopicItem])
def list_topics(username: str = Depends(get_current_username)) -> List[TopicItem]:
    users = cli.list_users()
    topics = cli.topics_from_users(users)
    result = []
    for topic in topics:
        accessors = []
        for user in users:
            if user.name in ("*", "everyone"):
                continue
            for access in user.accesses:
                if access.topic == topic and access.permission != "no":
                    accessors.append(
                        {"username": user.name, "permission": access.permission}
                    )
        result.append(TopicItem(name=topic, accessors=accessors))
    return result


@app.post("/topics/{topic}/access")
def grant_topic_access(
    topic: str,
    body: TopicAccessRequest,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.grant_access(body.username, topic, body.permission)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Access granted for {body.username} on {topic}"}


@app.delete("/topics/{topic}/access/{username}")
def revoke_topic_access(
    topic: str,
    username: str,
    current_username: str = Depends(get_current_username),
) -> dict:
    try:
        cli.revoke_access(username, topic)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Access revoked for {username} on {topic}"}


@app.delete("/topics/{topic}")
def delete_topic(
    topic: str,
    username: str = Depends(get_current_username),
) -> dict:
    try:
        users = cli.list_users()
        for user in users:
            for access in user.accesses:
                if access.topic == topic and access.permission != "no":
                    cli.revoke_access(user.name, topic)
    except cli.NtfyError as exc:
        raise _ntfy_error_response(exc)
    return {"detail": f"Topic {topic} deleted"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("ntfy_mgr_server.main:app", host=config.HOST, port=config.PORT, reload=True)
