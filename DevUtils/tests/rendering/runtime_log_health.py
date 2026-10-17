"""Separate only the integrated connection close after orderly client shutdown.

Every other error, earlier close, stack trace and native panic remains visible
to each caller's failure patterns. No elapsed-time or exception-wide exemption.
"""
import re

CLIENT_STOPPING = re.compile(r"^\[\d{2}:\d{2}:\d{2}\] \[Render thread/INFO\]: Stopping!$")
SHUTDOWN_DISCONNECT = re.compile(
    r"^\[\d{2}:\d{2}:\d{2}\] \[Server thread/INFO\]: \S+ lost connection: "
    r"Internal Exception: java\.nio\.channels\.ClosedChannelException$")


def separate_shutdown_disconnects(text: str) -> tuple[str, int]:
    stopping = False
    unexpected = []
    expected = 0
    for line in text.splitlines():
        if CLIENT_STOPPING.fullmatch(line):
            stopping = True
        if stopping and SHUTDOWN_DISCONNECT.fullmatch(line):
            expected += 1
        else:
            unexpected.append(line)
    return "\n".join(unexpected), expected
