import unittest
import unittest.mock

import numpy as np

from app.audio_split import (
    compute_rms_curve,
    merge_segments,
    snap_boundaries_to_beats,
    split_long_clips,
)


def _rms_for_quiet_gap(duration: float, quiet_start: float, quiet_end: float, sample_rate: int = 1000):
    """Synthetic signal: loud everywhere except [quiet_start, quiet_end)."""
    times = np.arange(0.0, duration, 1.0 / sample_rate)
    samples = np.full(len(times), 0.5, dtype=np.float32)
    quiet = (times >= quiet_start) & (times < quiet_end)
    samples[quiet] = 0.001
    return samples, sample_rate


class ComputeRmsCurveTests(unittest.TestCase):
    def test_loud_and_quiet_regions(self):
        samples, sample_rate = _rms_for_quiet_gap(4.0, 1.0, 3.0)
        times, rms = compute_rms_curve(samples, sample_rate, window=0.1, hop=0.05)
        loud = rms[(times < 0.5) | (times > 3.5)]
        quiet = rms[(times > 1.5) & (times < 2.5)]
        self.assertTrue(np.all(loud > 0.4))
        self.assertTrue(np.all(quiet < 0.01))

    def test_mono_mixdown_of_stereo(self):
        stereo = np.ones((1000, 2), dtype=np.float32) * 0.25
        times, rms = compute_rms_curve(stereo, 1000, window=0.1, hop=0.05)
        self.assertTrue(np.all(rms > 0.2))

    def test_empty_signal(self):
        times, rms = compute_rms_curve(np.array([], dtype=np.float32), 1000)
        self.assertEqual(len(times), 1)
        self.assertEqual(float(rms[0]), 0.0)


class MergeSegmentsTests(unittest.TestCase):
    def test_merges_close_segments(self):
        segments = [(1.0, 2.0), (2.4, 3.0), (5.0, 6.0)]
        self.assertEqual(merge_segments(segments, 0.5), [(1.0, 3.0), (5.0, 6.0)])

    def test_keeps_distant_segments(self):
        segments = [(1.0, 2.0), (3.0, 4.0)]
        self.assertEqual(merge_segments(segments, 0.5), [(1.0, 2.0), (3.0, 4.0)])

    def test_handles_unsorted_and_overlapping(self):
        segments = [(4.0, 5.0), (1.0, 2.0), (1.5, 1.8)]
        self.assertEqual(merge_segments(segments, 0.5), [(1.0, 2.0), (4.0, 5.0)])

    def test_empty(self):
        self.assertEqual(merge_segments([], 0.5), [])


class SplitLongClipsTests(unittest.TestCase):
    def test_short_clip_untouched(self):
        clips = [(2.0, 10.0)]
        times = np.array([0.0, 100.0])
        rms = np.array([1.0, 1.0])
        self.assertEqual(split_long_clips(clips, times, rms, 8.0, 15.0), [(2.0, 10.0)])

    def test_boundary_max_length_untouched(self):
        clips = [(0.0, 15.0)]
        times = np.linspace(0, 15, 100)
        rms = np.ones(100)
        self.assertEqual(split_long_clips(clips, times, rms, 8.0, 15.0), [(0.0, 15.0)])

    def test_long_clip_splits_at_quiet_point(self):
        samples, sample_rate = _rms_for_quiet_gap(20.0, 9.5, 11.5)
        times, rms = compute_rms_curve(samples, sample_rate, window=0.1, hop=0.05)
        result = split_long_clips([(0.0, 20.0)], times, rms, 8.0, 15.0)
        self.assertEqual(len(result), 2)
        cut = result[0][1]
        # The cut must land in the low-RMS region and respect [8, 12] window bounds.
        self.assertGreaterEqual(cut, 8.0)
        self.assertLessEqual(cut, 12.0)
        self.assertGreaterEqual(cut, 9.4)
        self.assertLessEqual(cut, 11.6)
        for start, end in result:
            self.assertGreaterEqual(end - start, 8.0 - 1e-6)
            self.assertLessEqual(end - start, 15.0 + 1e-6)

    def test_very_long_clip_splits_into_bounded_pieces(self):
        samples, sample_rate = _rms_for_quiet_gap(40.0, 0.0, 0.0)
        times, rms = compute_rms_curve(samples, sample_rate, window=0.1, hop=0.05)
        result = split_long_clips([(0.0, 40.0)], times, rms, 8.0, 15.0)
        self.assertGreaterEqual(len(result), 3)
        for start, end in result:
            self.assertGreaterEqual(end - start, 8.0 - 1e-6)
            self.assertLessEqual(end - start, 15.0 + 1e-6)
        # Contiguity and coverage
        self.assertEqual(result[0][0], 0.0)
        self.assertEqual(result[-1][1], 40.0)
        for left, right in zip(result, result[1:]):
            self.assertEqual(left[1], right[0])

    def test_no_valid_window_keeps_clip(self):
        # min_len == max_len leaves no room; 16s clip with min 15 max 15 cannot split cleanly
        clips = [(0.0, 16.0)]
        times = np.linspace(0, 16, 100)
        rms = np.ones(100)
        result = split_long_clips(clips, times, rms, 15.0, 15.0)
        self.assertEqual(result, [(0.0, 16.0)])

    def test_invalid_lengths_raise(self):
        with self.assertRaises(ValueError):
            split_long_clips([(0.0, 20.0)], np.array([0.0]), np.array([1.0]), 0.0, 15.0)
        with self.assertRaises(ValueError):
            split_long_clips([(0.0, 20.0)], np.array([0.0]), np.array([1.0]), 16.0, 15.0)


class SnapBoundariesToBeatsTests(unittest.TestCase):
    def test_boundary_snaps_to_nearest_beat(self):
        clips = [(0.0, 10.0), (10.0, 20.0)]
        beats = np.array([9.7, 19.8])
        result = snap_boundaries_to_beats(clips, beats, 0.4, 8.0)
        self.assertEqual(result[0], (0.0, 9.7))
        self.assertEqual(result[1], (9.7, 20.0))

    def test_snap_rejected_when_min_length_violated(self):
        clips = [(0.0, 10.0), (10.0, 20.0)]
        beats = np.array([7.5])  # first piece would be 7.5 < 8: reject
        result = snap_boundaries_to_beats(clips, beats, 3.0, 8.0)
        self.assertEqual(result, [(0.0, 10.0), (10.0, 20.0)])
        beats = np.array([9.2])  # both pieces stay >= 8: accept
        result = snap_boundaries_to_beats(clips, beats, 1.0, 8.0)
        self.assertEqual(result[0][1], 9.2)

    def test_no_beats_within_tolerance_keeps_boundary(self):
        clips = [(0.0, 10.0), (10.0, 20.0)]
        beats = np.array([5.0, 15.0])
        result = snap_boundaries_to_beats(clips, beats, 0.4, 8.0)
        self.assertEqual(result, clips)

    def test_single_clip_unchanged(self):
        clips = [(0.0, 10.0)]
        beats = np.array([5.0])
        self.assertEqual(snap_boundaries_to_beats(clips, beats, 0.4, 8.0), clips)

    def test_multiple_boundaries(self):
        clips = [(0.0, 12.0), (12.0, 24.0), (24.0, 36.0)]
        beats = np.array([12.3, 23.8])
        result = snap_boundaries_to_beats(clips, beats, 0.4, 8.0)
        self.assertEqual(result, [(0.0, 12.3), (12.3, 23.8), (23.8, 36.0)])

    def test_empty_beats(self):
        clips = [(0.0, 10.0), (10.0, 20.0)]
        self.assertEqual(snap_boundaries_to_beats(clips, np.array([]), 0.4, 8.0), clips)


if __name__ == "__main__":
    unittest.main()


class AudioSplitApiTests(unittest.TestCase):
    def setUp(self):
        from app import main as main_module
        from app.schemas import AudioClipResult, AudioSplitResult

        self.main = main_module
        self.project_id = "proj123"
        self.clip = AudioClipResult(
            index=1,
            start=0.0,
            end=10.0,
            output_url="/exports/vae_audio_x_part001.wav",
            output_path="D:/exports/vae_audio_x_part001.wav",
            output_size_bytes=1234,
        )
        self.result = AudioSplitResult(project_id=self.project_id, clips=[self.clip])

    def _make_manager(self, job_id="job123", status="running"):
        from app.schemas import AudioSplitJobStatus

        manager = unittest.mock.Mock()
        job = unittest.mock.Mock()
        job.job_id = job_id
        manager.start.return_value = job
        manager.status.return_value = AudioSplitJobStatus(
            project_id=self.project_id, job_id=job_id, status=status, stage="separate", progress=30.0
        )
        manager.cancel.return_value = AudioSplitJobStatus(
            project_id=self.project_id, job_id=job_id, status="cancelled"
        )
        manager.latest_result.return_value = self.result
        return manager

    def test_start_job(self):
        from app.schemas import AudioSplitSettings

        manager = self._make_manager()
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            response = self.main.start_audio_split_job(self.project_id, AudioSplitSettings())
        self.assertEqual(response.project_id, self.project_id)
        self.assertEqual(response.job_id, "job123")
        self.assertEqual(response.status, "queued")
        manager.start.assert_called_once()
        settings = manager.start.call_args[0][1]
        self.assertEqual(settings.max_clip_length, 15.0)
        self.assertEqual(settings.min_clip_length, 8.0)

    def test_start_job_project_not_found(self):
        from fastapi import HTTPException
        from app.schemas import AudioSplitSettings

        manager = self._make_manager()
        manager.start.side_effect = FileNotFoundError("nope")
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            with self.assertRaises(HTTPException) as ctx:
                self.main.start_audio_split_job(self.project_id, AudioSplitSettings())
        self.assertEqual(ctx.exception.status_code, 404)

    def test_start_job_no_audio_stream(self):
        from fastapi import HTTPException
        from app.schemas import AudioSplitSettings

        manager = self._make_manager()
        manager.start.side_effect = ValueError("no audio stream")
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            with self.assertRaises(HTTPException) as ctx:
                self.main.start_audio_split_job(self.project_id, AudioSplitSettings())
        self.assertEqual(ctx.exception.status_code, 400)

    def test_get_job_status(self):
        manager = self._make_manager()
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            status = self.main.get_audio_split_job(self.project_id, "job123")
        self.assertEqual(status.status, "running")
        self.assertEqual(status.stage, "separate")
        self.assertEqual(status.progress, 30.0)

    def test_get_job_status_not_found(self):
        from fastapi import HTTPException

        manager = self._make_manager()
        manager.status.side_effect = FileNotFoundError("nope")
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            with self.assertRaises(HTTPException) as ctx:
                self.main.get_audio_split_job(self.project_id, "job123")
        self.assertEqual(ctx.exception.status_code, 404)

    def test_cancel_job(self):
        manager = self._make_manager()
        with unittest.mock.patch.object(self.main, "audio_jobs", manager):
            status = self.main.cancel_audio_split_job(self.project_id, "job123")
        self.assertEqual(status.status, "cancelled")
        manager.cancel.assert_called_once_with(self.project_id, "job123")

    def test_get_result(self):
        manager = self._make_manager()
        with unittest.mock.patch.object(self.main, "audio_jobs", manager), unittest.mock.patch.object(
            self.main, "storage"
        ) as storage:
            result = self.main.get_audio_split_result(self.project_id)
        self.assertEqual(len(result.clips), 1)
        self.assertEqual(result.clips[0].output_url, "/exports/vae_audio_x_part001.wav")
        storage.load_project.assert_called_once_with(self.project_id)

    def test_get_result_empty_when_none(self):
        manager = self._make_manager()
        manager.latest_result.return_value = None
        with unittest.mock.patch.object(self.main, "audio_jobs", manager), unittest.mock.patch.object(
            self.main, "storage"
        ):
            result = self.main.get_audio_split_result(self.project_id)
        self.assertEqual(result.project_id, self.project_id)
        self.assertEqual(result.clips, [])


class AudioJobManagerTests(unittest.TestCase):
    def test_latest_result_reads_persisted_file(self):
        import json
        import tempfile
        from pathlib import Path

        from app.audio_jobs import AudioJobManager
        from app.schemas import AudioClipResult

        with tempfile.TemporaryDirectory() as tmp:
            storage = unittest.mock.Mock()
            storage.projects = Path(tmp)
            payload = {
                "project_id": "p1",
                "clips": [
                    AudioClipResult(index=1, start=0.0, end=5.0, output_url="/exports/a.wav").model_dump()
                ],
            }
            (Path(tmp) / "p1_audio.json").write_text(json.dumps(payload), encoding="utf-8")
            manager = AudioJobManager(unittest.mock.Mock(), storage)
            result = manager.latest_result("p1")
            self.assertIsNotNone(result)
            self.assertEqual(result.clips[0].end, 5.0)
            self.assertIsNone(manager.latest_result("missing"))


if __name__ == "__main__":
    unittest.main()
