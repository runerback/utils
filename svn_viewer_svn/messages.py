import requests
import os
from datetime import datetime
from svntypes import jtoken

_messages_api = os.environ["services__messages__http__0"]
assert _messages_api


def send_message(id: str, content: jtoken, sync=True):
    try:
        # requests.post(
        #     f"{_messages_api}/message?id={id}" + ("&sync" if sync else ""),
        #     json={
        #         **content,
        #         "timestamp": datetime.now.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3],
        #     },
        # )
        pass
    except:
        print(f"bao cuo la!!")
    # end try


# end def
