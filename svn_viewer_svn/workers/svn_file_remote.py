from workers.job import Job, JobPayload
from messages import send_message
from svn import svn_fetch_status, svn_file_remote


class fetch_svn_file_remote_job_payload(JobPayload):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_file_remote")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_file_remote_job(Job[fetch_svn_file_remote_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.id, {"processing": True, "job": self.payload.type})
        try:
            error, content = svn_file_remote(self.payload.path)
            if error:
                send_message(self.id, {"error": error, "job": self.payload.type})
                return
            # end if
            if content:
                send_message(
                    self.id,
                    {"data": content, "job": self.payload.type},
                )
            else:
                send_message(
                    self.id,
                    {"completed": True, "job": self.payload.type},
                )
            # end if
        except Exception as exp:
            send_message(self.id, {"error": str(exp), "job": self.payload.type})
        # end try

    # end def


# end class
