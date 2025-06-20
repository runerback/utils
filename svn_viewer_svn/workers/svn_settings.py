from svn import svn_fetch_settings
from workers.job import Job, JobPayload
from messages import send_message


class fetch_svn_settings_job_payload(JobPayload):
    def __init__(self, path: str, type: str | None = None):
        super().__init__(kind="fetch_svn_settings")
        self.path = path
        self.type = type

    # end def


# end class


class fetch_svn_settings_job(Job[fetch_svn_settings_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.id, {"processing": True, "job": self.payload.type})
        try:
            settings = svn_fetch_settings(self.payload.path)
            send_message(
                self.id,
                {"data": settings, "job": self.payload.type},
                preprocess=True,
            )
        except Exception as exp:
            send_message(self.id, {"error": str(exp), "job": self.payload.type})
        # end try

    # end def


# end class
