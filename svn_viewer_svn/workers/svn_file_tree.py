from messages import send_message
from svn import svn_fetch_file_tree
from workers.task import Task, TaskData


class fetch_svn_file_tree_task_data(TaskData):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_file_tree")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_file_tree_task(Task[fetch_svn_file_tree_task_data]):
    def __init__(self, id, data):
        super().__init__(id, data)

    # end def

    def _execute(self, logger):
        send_message(self.id, {"processing": True, "job": self.data.type})
        try:
            result = svn_fetch_file_tree(self.data.path)
            if result.error:
                send_message(self.id, {"error": result.error, "job": self.data.type})
                return
            # end if
            if result.nodes is not None:
                send_message(
                    self.id,
                    {"data": result.toJSON(), "job": self.data.type},
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
