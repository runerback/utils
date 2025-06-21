from svn import svn_fetch_settings
from workers.task import Task, TaskData
from messages import send_message


class fetch_svn_settings_task_data(TaskData):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_settings")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_settings_task(Task[fetch_svn_settings_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            settings = svn_fetch_settings(self.data.path)
            send_message(
                self.id,
                {"data": settings, "job": self.data.type},
                preprocess=True,
            )
        except Exception as exp:
            send_message(self.id, {"error": str(exp), "job": self.data.type})
        # end try

    # end def


# end class
