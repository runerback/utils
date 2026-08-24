# ntfy-mgr implementation plan

## 1. Goal
A minimal Python web UI to manage a local ntfy server: users, topic access rules, and user tokens.

After login, the page has **two main tabs**:
- **User-based management**: list/add/delete users; for each user view/grant/revoke topic accesses and view/add/delete tokens.
- **Topic-based management**: list topics that have access rules; for each topic view/grant/revoke user accesses.

## 2. Tech stack
- Python 3.x + Flask (server-rendered HTML, Jinja2 templates).
- No database of our own; all state lives in ntfy.
- No custom audit logging; ntfy itself records changes.
- Session cookie expires when the browser tab is closed (no permanent session lifetime).
- `ntfy` CLI is executed directly on the host (project runs as root).

## 3. Authentication
- Login page asks for the single admin username and password.
- Credentials are validated against the running ntfy server via `GET {NTFY_BASE_URL}/v1/account` using HTTP Basic Auth.
- Login succeeds only if the response is HTTP 200 and the returned `role` is `admin`.
- After login, Flask session stores `username` and `role`.
- Logout clears the session.

## 4. Exact ntfy CLI commands (from `ntfy-main/cmd`)

| Feature | Command |
|---|---|
| Validate admin | `GET /v1/account` with Basic Auth |
| List users/access | `ntfy user list` (alias for `ntfy access`) |
| Add user | `NTFY_PASSWORD={pwd} ntfy user add {username}` |
| Delete user | `ntfy user remove {username}` (aliases: `del`, `rm`) |
| Grant access | `ntfy access {username} {topic} {permission}` |
| Revoke access | `ntfy access {username} {topic} deny` (or `none`) |
| Reset user access | `ntfy access --reset {username}` |
| List tokens | `ntfy token list` |
| Add token | `ntfy token add --expires={exp} --label={label} {username}` |
| Delete token | `ntfy token remove {username} {token}` (aliases: `del`, `rm`) |

Permission values for grants: `read-write`/`rw`, `read-only`/`read`/`ro`, `write-only`/`write`/`wo`.

## 5. UI structure

### 5.1 Login page
- Username + password form.
- On error show "Invalid credentials or not an admin".

### 5.2 Management page
Top bar: current admin name + **Logout** button.
Two tabs:

#### Tab 1: Users
- Table of users (from `ntfy user list`), hiding the anonymous/everyone pseudo-user.
- **Add user**: username, password (always creates a regular `role=user`; the UI itself is used by the single admin).
- **Delete user**: confirmation, then `ntfy user remove`.
- Expandable row per user showing:
  - **Accesses**: topic + permission; **Revoke** button; **Grant access** form (topic + permission).
  - **Tokens**: token value, label, expiry, last access; **Delete** button; **Add token** form (expiry 5d/30d/365d/never, optional label).

#### Tab 2: Topics
- Derive topic list from the access rules returned by `ntfy user list`.
- **Create topic +**: popup with topic name input and a multi-select user list; each checked user has a permission dropdown. Submitting grants access for each selected user to the topic.
- Show each topic with the users who have access and their permissions.
- **Grant access** form: select user, choose permission.
- **Revoke** button per user-topic pair.
- **Delete topic**: confirmation, then revoke all access rules for that topic (equivalent to `ntfy access USER TOPIC deny` for every user with access).

## 6. Parsing ntfy output
- Parse `ntfy user list` text output:
  - `user {name} (role: {role}, tier: {tier})`
  - `- read-write/read-only/write-only/no access to topic {topic}`
- Parse `ntfy token list` text output:
  - `user {name}`
  - `- {token} (label), expires ..., accessed from ... at ...`
  - `- {token}, never expires, ...`

## 7. Project structure
```
ntfy-mgr/
├── app.py                 # Flask app, routes, session/auth
├── cli.py                 # ntfy CLI wrapper and output parsers
├── config.py              # Configuration from env/file
├── requirements.txt
├── ntfy-mgr.service       # systemd unit file
├── templates/
│   ├── base.html
│   ├── login.html
│   └── index.html         # Tabs for users and topics
├── static/
│   └── style.css
└── README.md              # Deployment instructions
```

## 8. Configuration
All configurable via environment variables or a `.env` file loaded by the app:

| Variable | Default | Meaning |
|---|---|---|
| `NTFY_MGR_HOST` | `0.0.0.0` | Bind host |
| `NTFY_MGR_PORT` | *(required)* | Bind port (>20000), set at deploy time via env |
| `NTFY_BASE_URL` | `http://localhost` | Running ntfy server URL |
| `NTFY_CLI` | `ntfy` | Path to ntfy binary |
| `SECRET_KEY` | *(required)* | Flask session signing key |

## 9. Security
- Flask session cookie is browser-session-only (no `PERMANENT_SESSION`).
- CSRF protection on all state-changing forms (Flask-WTF).
- All CLI calls use `subprocess.run(..., shell=False)` with arguments passed as a list.
- Input validation: usernames and topic names must match ntfy-allowed patterns.
- The service runs as root per requirement; restrict exposure via firewall/reverse proxy.

## 10. Deployment
1. Install dependencies: `pip install -r requirements.txt`.
2. Copy `ntfy-mgr.service` to `/etc/systemd/system/`.
3. Edit the service file and set the values of `NTFY_MGR_PORT`, `SECRET_KEY`, and `NTFY_BASE_URL` in the `[Service]` section.
4. Run `systemctl daemon-reload && systemctl enable --now ntfy-mgr`.
5. Open firewall for the chosen port as needed.

## 11. Implementation tasks
1. Bootstrap Flask project with config, session, and login/logout.
2. Build ntfy CLI wrapper (`cli.py`) with parsers for `user list` and `token list`.
3. Implement login validation via `/v1/account` Basic Auth.
4. Implement user CRUD and access management.
5. Implement token add/delete.
6. Build server-rendered UI with the two tabs.
7. Add CSRF protection and error handling.
8. Create `ntfy-mgr.service` and `README.md`.
9. Test locally against the running ntfy instance.
