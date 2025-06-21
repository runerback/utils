from logging import Logger
from workers.task import Task, TaskData
from messages import send_message
from svn import svn_file_remote


class fetch_svn_file_remote_task_data(TaskData):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_file_remote")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_file_remote_task(Task[fetch_svn_file_remote_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self, logger: Logger):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            error, content = svn_file_remote(self.data.path)
            if error:
                send_message(self.id, {"error": error, "job": self.data.type})
                return
            # end if
            if content:
                send_message(
                    self.id,
                    {"data": content, "job": self.data.type},
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
