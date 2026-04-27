from __future__ import annotations

import unittest

from src.segments import SegmentPlanner
from src.types import Detection


def make_detection(x: float = 960.0, y: float = 540.0) -> Detection:
    return Detection(bbox=(900.0, 500.0, 1020.0, 580.0), confidence=0.9, center_x=x, center_y=y)


class SegmentPlannerTests(unittest.TestCase):
    def test_short_bad_run_is_bridged(self) -> None:
        planner = SegmentPlanner(source_video="sample.mp4", grace_frames=2, min_segment_frames=2)
        planner.push_good(0, 0.0, make_detection(), (960.0, 540.0))
        planner.push_bad(1, 1 / 60.0, "bird_too_blurry", (960.0, 540.0))
        planner.push_good(2, 2 / 60.0, make_detection(970.0, 540.0), (965.0, 540.0))
        planner.finish()

        self.assertEqual(len(planner.segments), 1)
        self.assertEqual(planner.segments[0].frame_count, 3)
        self.assertTrue(planner.segments[0].accepted)

    def test_long_bad_run_splits_segment(self) -> None:
        planner = SegmentPlanner(source_video="sample.mp4", grace_frames=1, min_segment_frames=1)
        planner.push_good(0, 0.0, make_detection(), (960.0, 540.0))
        planner.push_bad(1, 1 / 60.0, "no_bird_detected", (960.0, 540.0))
        split_triggered = planner.push_bad(2, 2 / 60.0, "no_bird_detected", (960.0, 540.0))

        self.assertTrue(split_triggered)
        self.assertEqual(len(planner.segments), 1)
        self.assertEqual(planner.segments[0].frame_count, 1)
        self.assertEqual(planner.split_events[0].reason, "no_bird_detected")


if __name__ == "__main__":
    unittest.main()
