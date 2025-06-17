from messages import send_message
from svn import svn_fetch_diff
from workers.job import Job, JobPayload


class fetch_svn_diff_job_payload(JobPayload):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_diff")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_diff_job(Job[fetch_svn_diff_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.id, {"processing": True, "job": self.payload.type})
        try:
            error, message = svn_fetch_diff(self.payload.path)
            if error:
                send_message(self.id, {"error": error, "job": self.payload.type})
                return
            # end if
            if message:
                send_message(
                    self.id,
                    {"data": message, "job": self.payload.type},
                    preprocess=True,
                )
            # end if
        except TypeError as te:
            send_message(self.id, {"error": str(te), "job": self.payload.type})
        except Exception as exp:
            send_message(self.id, {"error": exp, "job": self.payload.type})
        # end try

    # end def


# end class
