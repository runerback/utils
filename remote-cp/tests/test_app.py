import io
import tempfile
import unittest
from pathlib import Path

import app as remote_cp_app


class RemoteCopyPasteAppTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        with remote_cp_app.STORE_LOCK:
            remote_cp_app.MESSAGE_STORE.clear()
        self.app = remote_cp_app.create_app(instance_path=self.temp_dir.name)
        self.app.config["TESTING"] = True
        self.client = self.app.test_client()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_index_renders_file_input_accept_from_backend_allowlist(self) -> None:
        response = self.client.get("/")

        self.assertEqual(response.status_code, 200)
        html = response.get_data(as_text=True)
        self.assertIn('.apk', remote_cp_app.FILE_INPUT_ACCEPT)
        self.assertIn(f'accept="{remote_cp_app.FILE_INPUT_ACCEPT}"', html)

    def test_apk_upload_is_accepted_as_generic_file_attachment(self) -> None:
        response = self.client.post(
            "/api/messages",
            data={
                "device_type": "Test device",
                "client_timestamp": "2026-04-30 13:17",
                "files": (io.BytesIO(b"apk payload"), "demo.apk", "application/octet-stream"),
            },
        )

        self.assertEqual(response.status_code, 201)
        payload = response.get_json()
        self.assertEqual(payload["text"], "")
        self.assertEqual(len(payload["files"]), 1)
        self.assertEqual(payload["files"][0]["name"], "demo.apk")
        self.assertTrue(payload["files"][0]["downloadUrl"].endswith("/demo.apk"))

        uploaded_files = list((Path(self.app.instance_path) / "uploads").iterdir())
        self.assertEqual(len(uploaded_files), 1)
        self.assertEqual(uploaded_files[0].suffix, ".apk")


if __name__ == "__main__":
    unittest.main()
