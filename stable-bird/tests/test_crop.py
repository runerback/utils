from __future__ import annotations

import unittest

from src.crop import solve_crop_window


class CropWindowTests(unittest.TestCase):
    def test_crop_window_preserves_source_bounds(self) -> None:
        crop_window = solve_crop_window(
            centers=[(960.0, 540.0), (1010.0, 535.0), (930.0, 555.0)],
            source_width=1920,
            source_height=1080,
        )

        self.assertGreater(crop_window.width, 0)
        self.assertGreater(crop_window.height, 0)
        self.assertLessEqual(crop_window.width, 1920)
        self.assertLessEqual(crop_window.height, 1080)
        self.assertAlmostEqual(crop_window.width / crop_window.height, 1920 / 1080, places=2)


if __name__ == "__main__":
    unittest.main()
