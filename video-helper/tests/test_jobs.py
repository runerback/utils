import time
import unittest
from unittest.mock import Mock, patch

from fastapi import HTTPException

from app.jobs import MAX_RETAINED_JOBS, Job, JobRunner
from app.main import create_preview_job, get_job
from app.schemas import JobCreateResponse, RenderResponse


def _wait_for_status(runner: JobRunner, job_id: str, timeout: float = 5.0) -> str:
    deadline = time.time() + timeout
    while time.time() < deadline:
        job = runner.get(job_id)
        if job is not None and job.status in ("done", "error"):
            return job.status
        time.sleep(0.01)
    raise AssertionError(f"Job {job_id} did not finish within {timeout}s")


class JobRunnerTest(unittest.TestCase):
    def test_successful_job_completes_with_result(self) -> None:
        runner = JobRunner()
        result = RenderResponse(project_id="p1", output_url="/work/preview.mp4")
        job = runner.submit("preview", "p1", lambda: result)
        self.assertEqual(_wait_for_status(runner, job.job_id), "done")
        finished = runner.get(job.job_id)
        self.assertEqual(finished.result["project_id"], "p1")
        self.assertEqual(finished.result["output_url"], "/work/preview.mp4")
        self.assertIsNone(finished.error)

    def test_failing_job_records_error(self) -> None:
        runner = JobRunner()

        def _boom():
            raise ValueError("render exploded")

        job = runner.submit("export", "p1", _boom)
        self.assertEqual(_wait_for_status(runner, job.job_id), "error")
        self.assertEqual(runner.get(job.job_id).error, "render exploded")

    def test_unknown_job_returns_none(self) -> None:
        runner = JobRunner()
        self.assertIsNone(runner.get("does-not-exist"))

    def test_history_is_capped(self) -> None:
        runner = JobRunner()
        for _ in range(MAX_RETAINED_JOBS + 10):
            runner.submit("preview", "p1", lambda: RenderResponse(project_id="p1"))
        self.assertEqual(len(runner._jobs), MAX_RETAINED_JOBS)


class JobEndpointsTest(unittest.TestCase):
    def test_create_preview_job_404_for_unknown_project(self) -> None:
        storage = Mock()
        storage.load_project.side_effect = FileNotFoundError("nope")
        with patch("app.main.storage", storage):
            with self.assertRaises(HTTPException) as ctx:
                create_preview_job("missing")
        self.assertEqual(ctx.exception.status_code, 404)

    def test_create_preview_job_submits_to_runner(self) -> None:
        storage = Mock()
        runner = Mock()
        runner.submit.return_value = Mock(job_id="job-123")
        with patch("app.main.storage", storage), patch("app.main.job_runner", runner):
            response = create_preview_job("p1")
        self.assertEqual(response, JobCreateResponse(job_id="job-123"))
        self.assertEqual(runner.submit.call_args.args[:2], ("preview", "p1"))

    def test_get_job_404_for_unknown_job(self) -> None:
        runner = Mock()
        runner.get.return_value = None
        with patch("app.main.job_runner", runner):
            with self.assertRaises(HTTPException) as ctx:
                get_job("missing")
        self.assertEqual(ctx.exception.status_code, 404)

    def test_get_job_returns_response(self) -> None:
        job = Job(
            job_id="j1",
            kind="preview",
            project_id="p1",
            status="done",
            result={"project_id": "p1", "format": "mp4", "output_url": "/work/preview.mp4", "parts": []},
            created_at=1.0,
            updated_at=2.0,
        )
        runner = Mock()
        runner.get.return_value = job
        with patch("app.main.job_runner", runner):
            response = get_job("j1")
        self.assertEqual(response.status, "done")
        self.assertEqual(response.result.output_url, "/work/preview.mp4")


if __name__ == "__main__":
    unittest.main()
