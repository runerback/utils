from __future__ import annotations


class ExponentialCenterTracker:
    def __init__(self, smoothing_alpha: float) -> None:
        self._alpha = smoothing_alpha
        self._current_center: tuple[float, float] | None = None

    @property
    def current_center(self) -> tuple[float, float] | None:
        return self._current_center

    def update(self, detection_center: tuple[float, float]) -> tuple[float, float]:
        if self._current_center is None:
            self._current_center = detection_center
            return self._current_center

        current_x, current_y = self._current_center
        next_x = (self._alpha * detection_center[0]) + ((1.0 - self._alpha) * current_x)
        next_y = (self._alpha * detection_center[1]) + ((1.0 - self._alpha) * current_y)
        self._current_center = (next_x, next_y)
        return self._current_center

    def reset(self) -> None:
        self._current_center = None

