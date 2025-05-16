import logging
from threading import Timer
from uuid import uuid4
from messages import send_message
from svntypes import jtoken
from abc import ABC, abstractmethod
from typing import Callable


class JobPayload(ABC):
    def __init__(self, kind: str):
        self.kind = kind

    # end def


# end class


class Job[TPayload: JobPayload]:
    def __init__(self, id: str, payload: TPayload):
        self.id = id
        self.payload = payload
        self.running: bool = False
        self.completed: bool = False
        self.failed: bool = False
        self.error: Exception | None = None

    # end def

    @property
    def finished(self):
        return self.completed or self.failed

    # end def

    @property
    def pending(self):
        return not self.running and not self.finished

    # end def

    def execute(self):
        if self.running or self.finished:
            return
        # end if
        self.running = True
        try:
            self._execute()
            self.completed = True
        except Exception as e:
            self.failed = False
            self.error = e
        finally:
            self.running = False
        # end try

    # end def

    @abstractmethod
    def _execute(self):
        pass

    # end def
    def __str__(self):
        return f"{{ id: {self.id}, payload: {self.payload} completed: {self.completed}, failed: {self.failed}, error: {self.error} }}"

    # end def


# end class


class send_message_job_payload(JobPayload):
    def __init__(self, id: str, content: jtoken, sync: bool | None = True):
        super().__init__(kind="send_message")
        self.id = id
        self.content = content
        self.sync = sync

    # end def


# end class


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


class send_message_job(Job[send_message_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.payload.id, self.payload.content, self.payload.sync)

    # end def


# end class

_scheduler = Scheduler()


def add_send_message_job(id: str, content: jtoken, sync=True):
    _scheduler.add(
        lambda jid: send_message_job(jid, send_message_job_payload(id, content, sync))
    )


# end def
