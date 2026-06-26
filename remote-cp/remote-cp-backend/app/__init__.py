from pathlib import Path

from flask import Flask
from flask_cors import CORS
from werkzeug.exceptions import RequestEntityTooLarge

from .config import CORS_ALLOWED_ORIGINS, MAX_UPLOAD_SIZE_BYTES
from .routes import health_bp, messages_bp, uploads_bp
from .sockets import socketio
from .storage import prepare_upload_folder


def create_app(instance_path: str | Path | None = None) -> Flask:
    flask_kwargs = {"instance_relative_config": True}
    if instance_path is not None:
        flask_kwargs["instance_path"] = str(Path(instance_path).resolve())

    app = Flask(__name__, **flask_kwargs)
    app.config["MAX_CONTENT_LENGTH"] = MAX_UPLOAD_SIZE_BYTES
    app.config["UPLOAD_FOLDER"] = Path(app.instance_path) / "uploads"

    upload_folder = app.config["UPLOAD_FOLDER"]
    prepare_upload_folder(upload_folder)

    CORS(app, origins=CORS_ALLOWED_ORIGINS, supports_credentials=False)
    socketio.init_app(app)

    app.register_blueprint(health_bp)
    app.register_blueprint(messages_bp)
    app.register_blueprint(uploads_bp)

    @app.errorhandler(RequestEntityTooLarge)
    def handle_file_too_large(_: RequestEntityTooLarge):
        from .config import MAX_UPLOAD_SIZE_MB

        return (
            jsonify(
                {
                    "error": (
                        f"The upload is too large. Keep the total request under {MAX_UPLOAD_SIZE_MB} MB."
                    )
                }
            ),
            413,
        )

    return app
