import io
import tempfile
import unittest
from pathlib import Path

from app import create_app
from app.models import message_store


class RemoteCopyPasteAppTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        message_store._messages.clear()
        self.app = create_app(instance_path=self.temp_dir.name)
        self.app.config["TESTING"] = True
        self.client = self.app.test_client()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_health_returns_ok(self) -> None:
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), {"status": "ok"})

    def test_get_messages_returns_empty_list(self) -> None:
        response = self.client.get("/api/messages")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json(), {"messages": []})

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

    def test_post_message_appears_in_get_messages(self) -> None:
        response = self.client.post(
            "/api/messages",
            data={
                "device_type": "Phone",
                "client_timestamp": "2026-05-27 10:00",
                "text": "Hello world",
            },
        )
        self.assertEqual(response.status_code, 201)

        response = self.client.get("/api/messages")
        self.assertEqual(response.status_code, 200)
        messages = response.get_json()["messages"]
        self.assertEqual(len(messages), 1)
        self.assertEqual(messages[0]["text"], "Hello world")
        self.assertEqual(messages[0]["deviceType"], "Phone")


if __name__ == "__main__":
    unittest.main()
