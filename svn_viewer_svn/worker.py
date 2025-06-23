import logging
from threading import Timer
from uuid import uuid4
from workers.svn_diff import fetch_svn_diff_task, fetch_svn_diff_task_data
from workers.svn_file_remote import (
    fetch_svn_file_remote_task,
    fetch_svn_file_remote_task_data,
)
from workers.svn_file_status import (
    fetch_svn_file_status_task,
    fetch_svn_file_status_task_data,
)
from workers.svn_file_tree import (
    fetch_svn_file_tree_task,
    fetch_svn_file_tree_task_data,
)
from workers.svn_info import fetch_svn_info_task, fetch_svn_info_task_data
from workers.svn_log_diffs import (
    fetch_svn_log_diffs_task,
    fetch_svn_log_diffs_task_data,
)
from workers.svn_logs import fetch_svn_logs_task, fetch_svn_logs_task_data
from workers.svn_status import fetch_svn_status_task, fetch_svn_status_task_data
from workers.svn_settings import (
    fetch_svn_settings_task,
    fetch_svn_settings_task_data,
)
from workers.task import Task
from typing import Callable

from workers.svn_unversioned import (
    fetch_svn_unversioned_task,
    fetch_svn_unversioned_task_data,
)


_logger = logging.getLogger("scheduler")


class Scheduler:
    jobs: dict[str, Task] = {}
    running_job_ids: list[str] = []

    def __init__(self, parallel: int = 2, interval: int = 1):
        assert parallel > 0, "parallel should be positive"
        assert interval > 0, "interval should be positive"
        self.interval = interval
        self.parallel = parallel
        self._nextloop()

    # end def

    @property
    def running_jobs_count(self):
        return len(self.running_job_ids)

    # end def

    def add[TJob: Task](self, factory: Callable[[str], TJob]):
        id = str(uuid4())
        job = self.jobs[id] = factory(id)
        _logger.info(
            f"job added - {id}: {job}, current {len(self.running_job_ids)} jobs"
        )
        return id

    # end def

    def _loop(self):
        try:
            if self.running_jobs_count > self.parallel:
                return
            # end if
            for pendingJobId in [
                key for key, value in self.jobs.items() if value.pending
            ]:
                self.running_job_ids.append(pendingJobId)
                _logger.info(f"executing job {pendingJobId}")
                self.jobs[pendingJobId].execute(_logger)
                self.running_job_ids.remove(pendingJobId)
                job = self.jobs.pop(pendingJobId)
                _logger.info(f"finish execute job {pendingJobId}: {job}")
                _logger.info(f"{len(self.running_job_ids)} jobs remained")
                if self.running_jobs_count > self.parallel:
                    break
                # end if
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


def create_fetch_svn_settings_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_settings_task(
            jid, fetch_svn_settings_task_data(path, type)
        )
    )


# end def


def create_fetch_svn_status_task(type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_status_task(jid, fetch_svn_status_task_data(type))
    )


# end def


def create_fetch_svn_diff_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_diff_task(jid, fetch_svn_diff_task_data(path, type))
    )


# end def


def create_fetch_svn_unversioned_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_unversioned_task(
            jid, fetch_svn_unversioned_task_data(path, type)
        )
    )


# end def


def create_fetch_svn_logs_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_logs_task(jid, fetch_svn_logs_task_data(path, type))
    )


# end def


def create_fetch_svn_log_diffs_task(
    path: str, n: int, m: int | None, type: str | None = None
):
    return _scheduler.add(
        lambda jid: fetch_svn_log_diffs_task(
            jid, fetch_svn_log_diffs_task_data(path, n, m, type)
        )
    )


# end def


def create_fetch_svn_file_status_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_status_task(
            jid, fetch_svn_file_status_task_data(path, type)
        )
    )


# end def


def create_fetch_svn_tree_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_tree_task(
            jid, fetch_svn_file_tree_task_data(path, type)
        )
    )


# end def


def create_fetch_svn_file_remote_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_file_remote_task(
            jid, fetch_svn_file_remote_task_data(path, type)
        )
    )


# end def


def create_fetch_svn_info_task(path: str, type: str | None = None):
    return _scheduler.add(
        lambda jid: fetch_svn_info_task(jid, fetch_svn_info_task_data(path, type))
    )


# end def
