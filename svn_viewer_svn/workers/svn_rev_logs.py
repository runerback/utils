from messages import send_message
from svn import svn_fetch_revision_logs
from workers.task import Task, TaskData


class fetch_svn_revision_logs_task_data(TaskData):
    def __init__(self, path: str, rev: int, type: str | None = None):
        super().__init__(kind="fetch_svn_revision_logs")
        self.path = path
        self.rev = rev
        self.type = type

    # end def


# end class


class fetch_svn_revision_logs_task(Task[fetch_svn_revision_logs_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self, logger):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            error, message = svn_fetch_revision_logs(
                self.data.path, self.data.rev, logger=logger
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
                error = "Unknown error"
            # end if
            send_message(self.id, {"error": error, "job": self.data.type})
        # end try

    # end def


# end class
