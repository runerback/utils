from logging import Logger
from messages import send_message
from svn import svn_fetch_log_diffs
from workers.task import Task, TaskData


class fetch_svn_log_diffs_task_data(TaskData):
    def __init__(self, path: str, n: int, m: int | None, type: str | None = None):
        super().__init__(kind="fetch_svn_log_diffs")
        self.path = path
        self.n = n
        self.m = m
        self.type = type

    # end def


# end class


class fetch_svn_log_diffs_task(Task[fetch_svn_log_diffs_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self, logger: Logger):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            error, message = svn_fetch_log_diffs(
                self.data.path, self.data.n, self.data.m
            )
            if error:
                send_message(self.id, {"error": error, "job": self.data.type})
                return
            # end if
            if message:
                send_message(
                    self.id,
                    {"data": message, "job": self.data.type},
                    preprocess=True,
                )
            else:
                send_message(
                    self.id,
                    {"completed": True, "job": self.data.type},
                )
            # end if
        except Exception as exp:
            logger.error(exp)
            error = str(exp)
            if not error:
                error = "failed"
            # end if
            send_message(self.id, {"error": error, "job": self.data.type})
        # end try

    # end def


# end class
