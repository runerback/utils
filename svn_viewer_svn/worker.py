from threading import Timer
from uuid import uuid4
from messages import send_message
from svntypes import jtoken
from abc import ABC, abstractmethod
from typing import Callable


class job_payload(ABC):
    def __init__(self, kind: str):
        self.kind = kind

    # end def


# end class


class job[TPayload: job_payload]:
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
    def started(self):
        return self.running or self.finished

    # end def

    def execute(self):
        if self.started or self.finished:
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


# end class


class send_message_job_payload(job_payload):
    def __init__(self, id: str, content: jtoken, sync: bool | None = True):
        super().__init__(kind="send_message")
        self.id = id
        self.content = content
        self.sync = sync

    # end def


# end class


class scheduler:
    jobs: dict[str, job] = []
    running_job_ids: list[str] = []

    def __init__(self):
        self.timer = Timer(1, self._loop, args=(self), deamon=True)
        self.timer.start()

    # end def

    def __del__(self):
        self.timer.cancel()

    # end def

    def add[TJob: job](self, factory: Callable[[str], TJob]):
        id = str(uuid4())
        self.jobs[id] = factory(id)

    # end def

    def _loop(self):
        if len(self.running_job_ids) > 0:
            return
        # end if
        # TODO: run jobs

    # end def


class send_message_job(job[send_message_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.payload.id, self.payload.content, self.payload.sync)

    # end def


# end class


def add_send_message_job(id: str, content: jtoken, sync=True):
    pass


# end def
