from workers.job import Job, JobPayload
from messages import send_message
from svntypes import jtoken


class send_message_job_payload(JobPayload):
    def __init__(self, id: str, content: jtoken, sync: bool | None = True):
        super().__init__(kind="send_message")
        self.id = id
        self.content = content
        self.sync = sync

    # end def


# end class


class send_message_job(Job[send_message_job_payload]):
    def __init__(self, id, payload):
        super().__init__(id, payload)

    # end def

    def _execute(self):
        send_message(self.payload.id, self.payload.content, self.payload.sync)

    # end def


# end class
