from __future__ import annotations

from src.cli import build_config, build_parser, load_config_data, main, resolve_setting

__all__ = ["build_config", "build_parser", "load_config_data", "main", "resolve_setting"]


if __name__ == "__main__":
    raise SystemExit(main())

