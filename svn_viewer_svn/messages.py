import requests
import os
from datetime import datetime
from svntypes import jtoken

_messages_api = os.environ["services__messages__http__0"]
assert _messages_api, "message api not configured"


def send_message(id: str, content: jtoken, preprocess=False):
    url = f"{_messages_api}/message?id={id}" + (
        "&preprocess=true" if preprocess else ""
    )
    requests.post(
        url=url,
        json={
            **content,
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
        },
    )


# end def
