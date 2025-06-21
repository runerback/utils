import logging
from threading import Timer
from uuid import uuid4
from workers.svn_diff import fetch_svn_diff_job, fetch_svn_diff_job_payload
from workers.svn_file_remote import (
    fetch_svn_file_remote_job,
    fetch_svn_file_remote_job_payload,
)
from workers.svn_file_status import (
    fetch_svn_file_status_job,
    fetch_svn_file_status_job_payload,
)
from workers.svn_file_tree import (
    fetch_svn_file_tree_job,
    fetch_svn_file_tree_job_payload,
)
from workers.svn_info import fetch_svn_info_job, fetch_svn_info_job_payload
from workers.svn_log_diffs import (
    fetch_svn_log_diffs_job,
    fetch_svn_log_diffs_job_payload,
)
from workers.svn_logs import fetch_svn_logs_job, fetch_svn_logs_job_payload
from workers.svn_status import fetch_svn_status_job, fetch_svn_status_job_payload
from workers.svn_settings import (
    fetch_svn_settings_job,
    fetch_svn_settings_job_payload,
)
from workers.job import Job
from typing import Callable

from workers.svn_unversioned import (
    fetch_svn_unversioned_job,
    fetch_svn_unversioned_job_payload,
)


_logger = logging.getLogger("scheduler")


class Scheduler:
    jobs: dict[str, Job] = {}
    running_job_ids: list[str] = []

    def __init__(self, max: int = 2, interval: int = 1):
        assert max > 0
        assert interval > 0
        self.interval = interval
        self.max = max
        self._nextloop()

    # end def

    @property
    def running_jobs_count(self):
        return len(self.running_job_ids)

    # end def

    def add[TJob: Job](self, factory: Callable[[str], TJob]):
        id = str(uuid4())
        job = self.jobs[id] = factory(id)
        _logger.info(
            f"job added - {id}: {job}, current {len(self.running_job_ids)} jobs"
        )
        return id

    # end def

    def _loop(self):
        try:
            if self.running_jobs_count > self.max:
                return
            # end if
            for pendingJobId in [
                key for key, value in self.jobs.items() if value.pending
            ]:
                self.running_job_ids.append(pendingJobId)
                _logger.info(f"executing job {pendingJobId}")
                self.jobs[pendingJobId].execute()
                self.running_job_ids.remove(pendingJobId)
                job = self.jobs.pop(pendingJobId)
                _logger.info(f"finish execute job {pendingJobId}: {job}")
                _logger.info(f"{len(self.running_job_ids)} jobs remained")
                break
            # end for
        finally:
            self._nextloop()

    # end def

    def _nextloop(self):
        self.timer = Timer(self.interval, self._loop)
        self.timer.daemon = True
        self.timer.start()

    # end def


# end class


_scheduler = Scheduler()


def get_running_jobs_count() -> int:
    return _scheduler.running_jobs_count


# end def


def add_fetch_svn_settings_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_settings_job(
            jid, fetch_svn_settings_job_payload(path, type)
        )
    )


# end def


def add_fetch_svn_status_job(type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_status_job(jid, fetch_svn_status_job_payload(type))
    )


# end def


def add_fetch_svn_diff_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_diff_job(jid, fetch_svn_diff_job_payload(path, type))
    )


# end def


def add_fetch_svn_unversioned_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_unversioned_job(
            jid, fetch_svn_unversioned_job_payload(path, type)
        )
    )


# end def


def add_fetch_svn_logs_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_logs_job(jid, fetch_svn_logs_job_payload(path, type))
    )


# end def


def add_fetch_svn_log_diffs_job(
    path: str, n: int, m: int | None, type: str | None = None
):
    return _scheduler.add(
        lambda jid: fetch_svn_log_diffs_job(
            jid, fetch_svn_log_diffs_job_payload(path, n, m, type)
        )
    )


# end def


def add_fetch_svn_file_status_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_status_job(
            jid, fetch_svn_file_status_job_payload(path, type)
        )
    )


# end def


def add_fetch_svn_tree_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_tree_job(
            jid, fetch_svn_file_tree_job_payload(path, type)
        )
    )


# end def


def add_fetch_svn_file_remote_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_remote_job(
            jid, fetch_svn_file_remote_job_payload(path, type)
        )
    )


# end def


def add_fetch_svn_info_job(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_info_job(jid, fetch_svn_info_job_payload(path, type))
    )


# end def
