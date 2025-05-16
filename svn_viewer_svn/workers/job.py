from abc import ABC, abstractmethod


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
            self.failed = True
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
