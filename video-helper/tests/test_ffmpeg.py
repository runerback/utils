import unittest
from unittest.mock import patch
from pathlib import Path

from app.ffmpeg import FFmpegService
from app.schemas import CropState, EditState, TrimState, VideoMetadata


class FFmpegServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.service = FFmpegService()
        self.metadata = VideoMetadata(width=1920, height=1080, duration=12.0, fps=30.0, frame_count=360)

    def test_build_preview_command_includes_filters(self) -> None:
        state = EditState(
            trim=TrimState(start=1.0, end=5.0),
            crop=CropState(x=10, y=20, width=1000, height=500),
            crop_enabled=True,
            resize_max=720,
            fps=24.0,
        )
        command = self.service.build_preview_command(Path("in.mp4"), Path("out.mp4"), self.metadata, state)
        self.assertIn("-vf", command)
        vf = command[command.index("-vf") + 1]
        self.assertIn("trim=start=1.0:end=5.0", vf)
        self.assertIn("crop=1000:500:10:20", vf)
        self.assertIn("scale=if(gte(iw\\,ih)\\,min(iw\\,720)\\,-2):if(gte(iw\\,ih)\\,-2\\,min(ih\\,720))", vf)
        self.assertIn("fps=24.0", vf)

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

    def test_build_player_proxy_command_transcodes_for_browser_playback(self) -> None:
        command = self.service.build_player_proxy_command(Path("in.mkv"), Path("player.mp4"))
        self.assertEqual(command[:4], ["ffmpeg", "-y", "-i", "in.mkv"])
        self.assertIn("libx264", command)
        self.assertIn("aac", command)
        self.assertIn("yuv420p", command)
        self.assertEqual(command[-1], "player.mp4")

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


if __name__ == "__main__":
    unittest.main()

