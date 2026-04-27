from __future__ import annotations

import logging
from pathlib import Path


LOG_FORMAT = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"


def configure_logging(log_dir: Path) -> Path:
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / "stable_bird.log"

    logger = logging.getLogger("stable_bird")
    logger.setLevel(logging.INFO)
    logger.propagate = False

    formatter = logging.Formatter(LOG_FORMAT)
    for handler in list(logger.handlers):
        logger.removeHandler(handler)
        handler.close()

    file_handler = logging.FileHandler(log_path, mode="w", encoding="utf-8")
    file_handler.setFormatter(formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)
    return log_path


def attach_video_trace(output_root: Path) -> tuple[Path, logging.Handler]:
    trace_path = output_root / "trace.log"
    handler = logging.FileHandler(trace_path, mode="w", encoding="utf-8")
    handler.setFormatter(logging.Formatter(LOG_FORMAT))
    logging.getLogger("stable_bird").addHandler(handler)
    return trace_path, handler


def detach_handler(handler: logging.Handler) -> None:
    logger = logging.getLogger("stable_bird")
    logger.removeHandler(handler)
    handler.close()
