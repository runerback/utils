import logging
from threading import Timer
from uuid import uuid4
from workers.svn_diff import fetch_svn_diff_job, fetch_svn_diff_job_payload
from workers.svn_logs import fetch_svn_logs_job, fetch_svn_logs_job_payload
from workers.svn_status import fetch_svn_status_job, fetch_svn_status_job_payload
from workers.send_message import send_message_job, send_message_job_payload
from workers.job import Job
from svntypes import jtoken
from typing import Callable

from workers.svn_unversioned import (
    fetch_svn_unversioned_job,
    fetch_svn_unversioned_job_payload,
)


_scheduler_timer_interval = 1
_logger = logging.getLogger("scheduler")


class Scheduler:
    jobs: dict[str, Job] = {}
    running_job_ids: list[str] = []

    def __init__(self):
        self._nextloop()

    # end def

    def add[TJob: Job](self, factory: Callable[[str], TJob]):
        id = str(uuid4())
        job = self.jobs[id] = factory(id)
        _logger.info(f"job added - {id}: {job}")
        return id

    # end def

    def _loop(self):
        try:
            if len(self.running_job_ids) > 0:
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
                break
            # end for
        finally:
            self._nextloop()

    # end def

    def _nextloop(self):
        self.timer = Timer(_scheduler_timer_interval, self._loop)
        self.timer.daemon = True
        self.timer.start()

    # end def


# end class


_scheduler = Scheduler()


def add_send_message_job(id: str, content: jtoken):
    _scheduler.add(
        lambda jid: send_message_job(jid, send_message_job_payload(id, content))
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
