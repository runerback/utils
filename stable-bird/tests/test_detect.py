from __future__ import annotations

import unittest

from src.detect import apply_tracking_anchor
from src.types import Detection


class DetectionAnchorTests(unittest.TestCase):
    def test_apply_tracking_anchor_uses_bbox_relative_percentages(self) -> None:
        detection = Detection(
            bbox=(100.0, 50.0, 200.0, 150.0),
            confidence=0.9,
            center_x=150.0,
            center_y=100.0,
        )

        apply_tracking_anchor(detection, 50.0, 35.0)

        self.assertAlmostEqual(detection.tracking_x, 150.0)
        self.assertAlmostEqual(detection.tracking_y, 85.0)
        self.assertEqual(detection.tracking_anchor_source, "bbox_relative")

    def test_apply_tracking_anchor_falls_back_to_bbox_center_for_invalid_bbox(self) -> None:
        detection = Detection(
            bbox=(120.0, 40.0, 120.0, 140.0),
            confidence=0.9,
            center_x=120.0,
            center_y=90.0,
        )

        apply_tracking_anchor(detection, 50.0, 35.0)

        self.assertEqual(detection.tracking_point, (120.0, 90.0))
        self.assertEqual(detection.tracking_anchor_source, "bbox_center_fallback")


if __name__ == "__main__":
    unittest.main()
