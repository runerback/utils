import requests
import os
from datetime import datetime
from svntypes import jtoken

_messages_api = os.environ["services__messages__http__0"]
assert _messages_api


def send_message(id: str, content: jtoken, sync=True):
    requests.post(
        f"{_messages_api}/message?id={id}" + ("&sync=true" if sync else ""),
        json={
            **content,
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
        },
    )


# end def
