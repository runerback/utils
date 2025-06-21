from messages import send_message
from svn import svn_fetch_info
from workers.task import Task, TaskData


class fetch_svn_info_task_data(TaskData):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_info")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_info_task(Task[fetch_svn_info_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            error, message = svn_fetch_info(self.data.path)
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
            send_message(self.id, {"error": str(exp), "job": self.data.type})
        # end try

    # end def


# end class
