import unittest
from pathlib import Path
from unittest.mock import patch

from app.ffmpeg import FFmpegService
from app.schemas import CropState, EditState, TrimState, VideoMetadata


class FFmpegServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.service = FFmpegService()
        self.metadata = VideoMetadata(
            width=1920,
            height=1080,
            duration=12.0,
            fps=30.0,
            frame_count=360,
            audio_codec="aac",
        )

    def test_build_preview_command_includes_filters(self) -> None:
        state = EditState(
            trim=TrimState(start=1.0, end=5.0),
            crop=CropState(x=10, y=20, width=1000, height=500),
            rotation={"quarter_turns": 1},
            crop_enabled=True,
            resize_max=720,
            fps=24.0,
        )
        command = self.service.build_preview_command(Path("in.mp4"), Path("out.mp4"), self.metadata, state)
        self.assertIn("-vf", command)
        vf = command[command.index("-vf") + 1]
        self.assertIn("trim=start=1.0:end=5.0", vf)
        self.assertIn("crop=1000:500:10:20", vf)
        self.assertIn("transpose=1", vf)
        self.assertIn("scale=if(gte(iw\\,ih)\\,min(iw\\,720)\\,-2):if(gte(iw\\,ih)\\,-2\\,min(ih\\,720))", vf)
        self.assertIn("fps=24.0", vf)
        self.assertLess(vf.index("crop=1000:500:10:20"), vf.index("transpose=1"))
        self.assertLess(vf.index("transpose=1"), vf.index("scale=if(gte(iw\\,ih)\\,min(iw\\,720)\\,-2):if(gte(iw\\,ih)\\,-2\\,min(ih\\,720))"))

    def test_build_preview_command_includes_resize_filter_for_portrait_metadata(self) -> None:
        metadata = VideoMetadata(width=1080, height=1920, duration=12.0, fps=30.0, frame_count=360)
        state = EditState(trim=TrimState(start=0.0, end=6.0), resize_max=960)
        command = self.service.build_preview_command(Path("in.mp4"), Path("out.mp4"), metadata, state)
        vf = command[command.index("-vf") + 1]
        self.assertIn("scale=if(gte(iw\\,ih)\\,min(iw\\,960)\\,-2):if(gte(iw\\,ih)\\,-2\\,min(ih\\,960))", vf)

    def test_build_preview_command_omits_crop_when_disabled(self) -> None:
        state = EditState(
            trim=TrimState(start=0.0, end=5.0),
            crop=CropState(x=10, y=20, width=1000, height=500),
            crop_enabled=False,
        )
        command = self.service.build_preview_command(Path("in.mp4"), Path("out.mp4"), self.metadata, state)
        vf = command[command.index("-vf") + 1]
        self.assertNotIn("crop=1000:500:10:20", vf)

    def test_build_preview_segment_command_overrides_trim_and_sets_h264_aac(self) -> None:
        state = EditState(
            trim=TrimState(start=1.0, end=10.0),
            crop=CropState(x=10, y=20, width=1000, height=500),
            rotation={"quarter_turns": 3},
            crop_enabled=True,
            resize_max=720,
            fps=24.0,
        )
        command = self.service.build_preview_segment_command(
            Path("in.mp4"),
            Path("out_part001.mp4"),
            self.metadata,
            state,
            segment_start=2.0,
            segment_end=4.5,
        )
        vf = command[command.index("-vf") + 1]
        self.assertIn("trim=start=2.0:end=4.5", vf)
        self.assertIn("crop=1000:500:10:20", vf)
        self.assertIn("transpose=2", vf)
        self.assertIn("fps=24.0", vf)
        af = command[command.index("-af") + 1]
        self.assertEqual(af, "atrim=start=2.0:end=4.5,asetpts=PTS-STARTPTS")
        self.assertEqual(command[command.index("-map") + 1:command.index("-map") + 4], ["0:v:0", "-map", "0:a?"])
        self.assertIn("libx264", command)
        self.assertIn("aac", command)

    def test_build_export_segment_command_overrides_trim_and_sets_h264_aac(self) -> None:
        state = EditState(trim=TrimState(start=0.0, end=8.0))
        command = self.service.build_export_segment_command(
            Path("in.mp4"),
            Path("out_part001.mp4"),
            self.metadata,
            state,
            segment_start=3.0,
            segment_end=8.0,
        )
        vf = command[command.index("-vf") + 1]
        self.assertIn("trim=start=3.0:end=8.0", vf)
        af = command[command.index("-af") + 1]
        self.assertEqual(af, "atrim=start=3.0:end=8.0,asetpts=PTS-STARTPTS")
        self.assertIn("libx264", command)
        self.assertIn("aac", command)

    def test_build_export_gif_command_uses_palette_pipeline(self) -> None:
        state = EditState(trim=TrimState(start=1.0, end=5.0), fps=12.0, resize_max=640)
        command = self.service.build_export_gif_command(Path("in.mp4"), Path("out.gif"), self.metadata, state)
        self.assertIn("-filter_complex", command)
        filter_complex = command[command.index("-filter_complex") + 1]
        self.assertIn("[0:v]trim=start=1.0:end=5.0", filter_complex)
        self.assertIn("fps=12.0", filter_complex)
        self.assertIn("palettegen=stats_mode=diff", filter_complex)
        self.assertIn("paletteuse=dither=sierra2_4a", filter_complex)
        self.assertIn("-an", command)
        self.assertEqual(command[-1], "out.gif")

    def test_build_export_gif_segment_command_overrides_trim(self) -> None:
        state = EditState(trim=TrimState(start=0.0, end=8.0), fps=10.0)
        command = self.service.build_export_gif_segment_command(
            Path("in.mp4"),
            Path("out_part001.gif"),
            self.metadata,
            state,
            segment_start=2.0,
            segment_end=4.0,
        )
        filter_complex = command[command.index("-filter_complex") + 1]
        self.assertIn("[0:v]trim=start=2.0:end=4.0", filter_complex)
        self.assertIn("fps=10.0", filter_complex)
        self.assertEqual(command[-1], "out_part001.gif")

    def test_build_preview_segment_command_disables_audio_when_source_has_none(self) -> None:
        silent_metadata = self.metadata.model_copy(update={"audio_codec": None})
        command = self.service.build_preview_segment_command(
            Path("in.mp4"),
            Path("out_part001.mp4"),
            silent_metadata,
            EditState(trim=TrimState(start=0.0, end=8.0)),
            segment_start=1.0,
            segment_end=3.0,
        )
        self.assertIn("-an", command)
        self.assertNotIn("-af", command)

    def test_validate_state_rejects_invalid_trim(self) -> None:
        state = EditState(trim=TrimState(start=8.0, end=9.0))
        self.service.validate_state(self.metadata, state)
        with self.assertRaises(ValueError):
            self.service.validate_state(self.metadata, EditState(trim=TrimState(start=11.0, end=13.0)))

    def test_validate_state_rejects_out_of_bounds_crop(self) -> None:
        state = EditState(crop=CropState(x=1700, y=900, width=400, height=300), crop_enabled=True)
        with self.assertRaises(ValueError):
            self.service.validate_state(self.metadata, state)

    def test_validate_state_allows_out_of_bounds_crop_when_disabled(self) -> None:
        state = EditState(crop=CropState(x=1700, y=900, width=400, height=300), crop_enabled=False)
        self.service.validate_state(self.metadata, state)

    def test_validate_state_rejects_small_resize_max(self) -> None:
        with self.assertRaises(ValueError):
            self.service.validate_state(self.metadata, EditState(resize_max=1))

    def test_edit_state_defaults_crop_enabled_off(self) -> None:
        self.assertFalse(EditState().crop_enabled)

    def test_edit_state_defaults_scene_split_configuration(self) -> None:
        scene_split = EditState().scene_split
        self.assertFalse(scene_split.enabled)
        self.assertEqual(scene_split.detector, "ffmpeg")
        self.assertEqual(scene_split.threshold, 0.4)
        self.assertEqual(scene_split.ai_sensitivity, 0.5)
        self.assertEqual(scene_split.min_clip_length, 2.0)
        self.assertEqual(scene_split.max_clip_length, 12.0)
        self.assertEqual(scene_split.selected_clip_indexes, [])

    def test_edit_state_defaults_rotation_configuration(self) -> None:
        rotation = EditState().rotation
        self.assertEqual(rotation.quarter_turns, 0)

    def test_edit_state_partial_rotation_payload_keeps_defaults(self) -> None:
        rotation = EditState(rotation={}).rotation
        self.assertEqual(rotation.quarter_turns, 0)

    def test_edit_state_rejects_out_of_range_rotation_turns(self) -> None:
        with self.assertRaises(ValueError):
            EditState(rotation={"quarter_turns": -1})
        with self.assertRaises(ValueError):
            EditState(rotation={"quarter_turns": 4})

    def test_edit_state_partial_scene_split_payload_keeps_defaults(self) -> None:
        scene_split = EditState(scene_split={"enabled": True}).scene_split
        self.assertTrue(scene_split.enabled)
        self.assertEqual(scene_split.detector, "ffmpeg")
        self.assertEqual(scene_split.threshold, 0.4)
        self.assertEqual(scene_split.ai_sensitivity, 0.5)
        self.assertEqual(scene_split.min_clip_length, 2.0)
        self.assertEqual(scene_split.max_clip_length, 12.0)
        self.assertEqual(scene_split.selected_clip_indexes, [])

    def test_edit_state_rejects_out_of_range_scene_split_threshold(self) -> None:
        with self.assertRaises(ValueError):
            EditState(scene_split={"threshold": 0})
        with self.assertRaises(ValueError):
            EditState(scene_split={"threshold": 1.1})

    def test_edit_state_accepts_scene_split_threshold_boundaries(self) -> None:
        near_zero = EditState(scene_split={"threshold": 1e-6}).scene_split.threshold
        one = EditState(scene_split={"threshold": 1.0}).scene_split.threshold
        self.assertEqual(near_zero, 1e-6)
        self.assertEqual(one, 1.0)

    def test_edit_state_accepts_ai_scene_split_detector(self) -> None:
        scene_split = EditState(scene_split={"detector": "ai", "ai_sensitivity": 0.7}).scene_split
        self.assertEqual(scene_split.detector, "ai")
        self.assertEqual(scene_split.ai_sensitivity, 0.7)

    def test_edit_state_rejects_out_of_range_ai_sensitivity(self) -> None:
        with self.assertRaises(ValueError):
            EditState(scene_split={"ai_sensitivity": 0})
        with self.assertRaises(ValueError):
            EditState(scene_split={"ai_sensitivity": 1.1})

    def test_edit_state_rejects_non_positive_scene_split_clip_lengths(self) -> None:
        with self.assertRaises(ValueError):
            EditState(scene_split={"min_clip_length": 0})
        with self.assertRaises(ValueError):
            EditState(scene_split={"max_clip_length": 0})

    def test_edit_state_accepts_scene_split_equal_min_max_lengths(self) -> None:
        scene_split = EditState(scene_split={"min_clip_length": 4.0, "max_clip_length": 4.0}).scene_split
        self.assertEqual(scene_split.min_clip_length, 4.0)
        self.assertEqual(scene_split.max_clip_length, 4.0)

    def test_edit_state_normalizes_selected_scene_split_clip_indexes(self) -> None:
        scene_split = EditState(scene_split={"selected_clip_indexes": [3, 1, 3, 2]}).scene_split
        self.assertEqual(scene_split.selected_clip_indexes, [1, 2, 3])

    def test_edit_state_rejects_non_positive_selected_scene_split_clip_indexes(self) -> None:
        with self.assertRaises(ValueError):
            EditState(scene_split={"selected_clip_indexes": [0]})

    def test_edit_state_rejects_scene_split_min_greater_than_max(self) -> None:
        with self.assertRaises(ValueError):
            EditState(scene_split={"min_clip_length": 8.0, "max_clip_length": 5.0})

    def test_build_player_proxy_command_transcodes_for_browser_playback(self) -> None:
        command = self.service.build_player_proxy_command(Path("in.mkv"), Path("player.mp4"))
        self.assertEqual(command[:4], ["ffmpeg", "-y", "-i", "in.mkv"])
        self.assertIn("libx264", command)
        self.assertIn("aac", command)
        self.assertIn("yuv420p", command)
        self.assertEqual(command[-1], "player.mp4")

    def test_build_preview_command_uses_double_transpose_for_180_rotation(self) -> None:
        state = EditState(rotation={"quarter_turns": 2})
        command = self.service.build_preview_command(Path("in.mp4"), Path("out.mp4"), self.metadata, state)
        vf = command[command.index("-vf") + 1]
        self.assertIn("transpose=1,transpose=1", vf)

    def test_requires_player_proxy_for_hevc_sources(self) -> None:
        hevc_metadata = self.metadata.model_copy(update={"video_codec": "hevc"})
        h264_metadata = self.metadata.model_copy(update={"video_codec": "h264"})
        self.assertTrue(self.service.requires_player_proxy(hevc_metadata))
        self.assertFalse(self.service.requires_player_proxy(h264_metadata))

    def test_probe_reads_codec_and_container_metadata(self) -> None:
        probe_output = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "hevc",
              "width": 1920,
              "height": 1080,
              "duration": "12.0",
              "avg_frame_rate": "30000/1001"
            },
            {
              "codec_type": "audio",
              "codec_name": "aac"
            }
          ],
          "format": {
            "duration": "12.0",
            "format_name": "mov,mp4,m4a,3gp,3g2,mj2"
          }
        }
        """
        with patch("app.ffmpeg.subprocess.run") as mock_run:
            mock_run.return_value.stdout = probe_output
            metadata = self.service.probe(Path("clip.mp4"))

        self.assertEqual(metadata.video_codec, "hevc")
        self.assertEqual(metadata.audio_codec, "aac")
        self.assertEqual(metadata.container_format, "mov,mp4,m4a,3gp,3g2,mj2")

    def test_probe_decodes_byte_output_for_unicode_paths(self) -> None:
        probe_output = """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1280,
              "height": 720,
              "duration": "6.0",
              "avg_frame_rate": "30/1"
            }
          ],
          "format": {
            "duration": "6.0",
            "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
            "filename": "日本語の動画.mp4"
          }
        }
        """.encode("utf-8")
        source = Path("日本語の動画.mp4")
        with patch("app.ffmpeg.subprocess.run") as mock_run:
            mock_run.return_value.stdout = probe_output
            mock_run.return_value.stderr = b""
            metadata = self.service.probe(source)

        command = mock_run.call_args.args[0]
        self.assertEqual(command[-1], str(source))
        self.assertEqual(metadata.width, 1280)
        self.assertEqual(metadata.height, 720)
        self.assertEqual(metadata.video_codec, "h264")

    def test_probe_raises_clear_error_when_stdout_missing(self) -> None:
        with patch("app.ffmpeg.subprocess.run") as mock_run:
            mock_run.return_value.stdout = None
            mock_run.return_value.stderr = b"failed to open file"
            with self.assertRaisesRegex(ValueError, "ffprobe returned no JSON output"):
                self.service.probe(Path("clip.mp4"))

    def test_parse_scene_changes_returns_empty_for_no_matches(self) -> None:
        output = "[h264 @ 123] no showinfo lines here"
        self.assertEqual(self.service.parse_scene_changes(output), [])

    def test_parse_scene_changes_ignores_malformed_lines(self) -> None:
        output = "\n".join(
            [
                "[Parsed_showinfo_1] n:0 pts:120 pts_time:not-a-number",
                "[Parsed_showinfo_1] n:1 pts:240 pts_time:2.500000",
                "[Parsed_showinfo_1] n:2 pts:360 pts_time:",
                "[Parsed_showinfo_1] n:3 pts:480 pts_time:-0.500000",
            ]
        )
        self.assertEqual(self.service.parse_scene_changes(output), [2.5])

    def test_parse_scene_changes_returns_sorted_unique_timestamps(self) -> None:
        output = "\n".join(
            [
                "[Parsed_showinfo_1] n:4 pts:300 pts_time:3.000000",
                "[Parsed_showinfo_1] n:1 pts:100 pts_time:1.000000",
                "[Parsed_showinfo_1] n:2 pts:100 pts_time:1.000400",
                "[Parsed_showinfo_1] n:3 pts:200 pts_time:2.000000",
                "[Parsed_showinfo_1] n:5 pts:300 pts_time:3.000000",
            ]
        )
        self.assertEqual(self.service.parse_scene_changes(output), [1.0, 2.0, 3.0])

    def test_detect_scene_changes_runs_ffmpeg_and_parses_output(self) -> None:
        with patch("app.ffmpeg.subprocess.run") as mock_run:
            mock_run.return_value.stdout = ""
            mock_run.return_value.stderr = (
                "[Parsed_showinfo_1] n:0 pts:45 pts_time:0.450000\n"
                "[Parsed_showinfo_1] n:1 pts:125 pts_time:1.250000\n"
            )
            timestamps = self.service.detect_scene_changes(Path("clip.mp4"), threshold=0.4)

        command = mock_run.call_args.args[0]
        self.assertIn("select='gt(scene\\,0.400000)',showinfo", command)
        self.assertEqual(timestamps, [0.45, 1.25])

    def test_build_scene_detection_command_rejects_invalid_threshold(self) -> None:
        with self.assertRaises(ValueError):
            self.service.build_scene_detection_command(Path("clip.mp4"), threshold=0)
        with self.assertRaises(ValueError):
            self.service.build_scene_detection_command(Path("clip.mp4"), threshold=1.5)

    def test_build_scene_split_segments_prefers_feasible_candidate_cuts(self) -> None:
        segments = self.service.build_scene_split_segments(
            duration=20.0,
            candidate_cuts=[3.0, 5.0, 9.0, 13.0, 18.0],
            min_len=4.0,
            max_len=8.0,
        )
        self.assertEqual(segments, [(0.0, 5.0), (5.0, 13.0), (13.0, 20.0)])

    def test_build_scene_split_segments_adds_synthetic_cuts_when_candidates_sparse(self) -> None:
        segments = self.service.build_scene_split_segments(
            duration=25.0,
            candidate_cuts=[7.0],
            min_len=6.0,
            max_len=10.0,
        )
        self.assertEqual(segments, [(0.0, 7.0), (7.0, 17.0), (17.0, 25.0)])

    def test_build_scene_split_segments_skips_dense_cuts_to_preserve_min_length(self) -> None:
        segments = self.service.build_scene_split_segments(
            duration=14.0,
            candidate_cuts=[1.0, 2.0, 4.1, 4.5, 5.0, 5.2, 8.0, 8.1, 9.9, 10.2],
            min_len=4.0,
            max_len=6.0,
        )
        self.assertEqual(segments, [(0.0, 5.2), (5.2, 9.9), (9.9, 14.0)])

    def test_build_scene_split_segments_ignores_duplicate_and_out_of_range_cuts(self) -> None:
        segments = self.service.build_scene_split_segments(
            duration=12.0,
            candidate_cuts=[-1.0, 3.0, 3.0000001, 12.0, 15.0],
            min_len=3.0,
            max_len=6.0,
        )
        self.assertEqual(segments, [(0.0, 3.0), (3.0, 9.0), (9.0, 12.0)])

    def test_build_scene_split_segments_guarantees_contiguous_coverage_within_bounds(self) -> None:
        segments = self.service.build_scene_split_segments(
            duration=23.0,
            candidate_cuts=[2.0, 4.1, 6.2, 10.0, 14.4, 17.0, 20.7],
            min_len=3.0,
            max_len=7.0,
        )
        self.assertGreaterEqual(len(segments), 1)
        self.assertEqual(segments[0][0], 0.0)
        self.assertEqual(segments[-1][1], 23.0)
        for index, (start, end) in enumerate(segments):
            self.assertGreater(end, start)
            self.assertGreaterEqual((end - start) + 1e-6, 3.0)
            self.assertLessEqual((end - start) - 1e-6, 7.0)
            if index > 0:
                self.assertAlmostEqual(start, segments[index - 1][1], places=6)

    def test_build_scene_split_segments_rejects_invalid_input(self) -> None:
        with self.assertRaises(ValueError):
            self.service.build_scene_split_segments(duration=0.0, candidate_cuts=[], min_len=2.0, max_len=6.0)
        with self.assertRaises(ValueError):
            self.service.build_scene_split_segments(duration=10.0, candidate_cuts=[], min_len=0.0, max_len=6.0)
        with self.assertRaises(ValueError):
            self.service.build_scene_split_segments(duration=10.0, candidate_cuts=[], min_len=7.0, max_len=6.0)

    def test_build_scene_split_segments_rejects_unsegmentable_short_duration(self) -> None:
        with self.assertRaises(ValueError):
            self.service.build_scene_split_segments(duration=1.5, candidate_cuts=[], min_len=2.0, max_len=5.0)

    def test_build_scene_split_segments_rejects_unsegmentable_duration_range_combo(self) -> None:
        with self.assertRaises(ValueError):
            self.service.build_scene_split_segments(duration=11.0, candidate_cuts=[5.0], min_len=4.0, max_len=5.0)

    def test_estimate_gif_size_reflects_resize_rotation_and_duration(self) -> None:
        state = EditState(
            trim=TrimState(start=1.0, end=4.0),
            crop=CropState(x=0, y=0, width=640, height=360),
            crop_enabled=True,
            rotation={"quarter_turns": 1},
            resize_max=320,
            fps=12.0,
        )
        estimated = self.service.estimate_gif_size(self.metadata, state)
        self.assertGreater(estimated, 0)

        no_resize_estimated = self.service.estimate_gif_size(
            self.metadata,
            EditState(
                trim=TrimState(start=1.0, end=4.0),
                crop=CropState(x=0, y=0, width=640, height=360),
                crop_enabled=True,
                rotation={"quarter_turns": 1},
                fps=12.0,
            ),
        )
        self.assertLess(estimated, no_resize_estimated)


if __name__ == "__main__":
    unittest.main()

