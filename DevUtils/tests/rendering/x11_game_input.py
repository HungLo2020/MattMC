"""Bounded ordinary keyboard input addressed to a verified isolated game window."""
from __future__ import annotations

import ctypes as C
import ctypes.util
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "Common"))
from capture_runner import find_linux_client_window_id, window_capture_provenance
from CaptureWindowVideo import process_start


class KeyEvent(C.Structure):
    _fields_ = [("type", C.c_int), ("serial", C.c_ulong), ("send_event", C.c_int),
                ("display", C.c_void_p), ("window", C.c_ulong), ("root", C.c_ulong),
                ("subwindow", C.c_ulong), ("time", C.c_ulong),
                ("x", C.c_int), ("y", C.c_int), ("x_root", C.c_int), ("y_root", C.c_int),
                ("state", C.c_uint), ("keycode", C.c_uint), ("same_screen", C.c_int)]


class Event(C.Union):
    _fields_ = [("key", KeyEvent), ("padding", C.c_long * 24)]


class GameInput:
    def __init__(self, pid: int):
        self.pid = pid
        self.identity = process_start(pid)
        self.target = find_linux_client_window_id(pid)
        if not self.target or window_capture_provenance("linux", self.target, pid).get("status") != "verified":
            raise RuntimeError("no verified live game window")
        self.window = int(self.target, 16)
        self.x = C.CDLL(ctypes.util.find_library("X11"))
        self.x.XOpenDisplay.argtypes = [C.c_char_p]
        self.x.XOpenDisplay.restype = C.c_void_p
        self.x.XDefaultRootWindow.argtypes = [C.c_void_p]
        self.x.XDefaultRootWindow.restype = C.c_ulong
        self.x.XStringToKeysym.argtypes = [C.c_char_p]
        self.x.XStringToKeysym.restype = C.c_ulong
        self.x.XKeysymToKeycode.argtypes = [C.c_void_p, C.c_ulong]
        self.x.XKeysymToKeycode.restype = C.c_uint
        self.x.XSendEvent.argtypes = [C.c_void_p, C.c_ulong, C.c_int, C.c_long, C.POINTER(Event)]
        self.x.XSendEvent.restype = C.c_int
        self.x.XGetInputFocus.argtypes = [C.c_void_p, C.POINTER(C.c_ulong), C.POINTER(C.c_int)]
        self.x.XSetInputFocus.argtypes = [C.c_void_p, C.c_ulong, C.c_int, C.c_ulong]
        self.x.XRaiseWindow.argtypes = [C.c_void_p, C.c_ulong]
        self.x.XSync.argtypes = [C.c_void_p, C.c_int]
        self.x.XCloseDisplay.argtypes = [C.c_void_p]
        self.x.XSetErrorHandler.argtypes = [C.c_void_p]
        self.x.XSetErrorHandler.restype = C.c_void_p
        self.errors: list[str] = []
        callback_type = C.CFUNCTYPE(C.c_int, C.c_void_p, C.c_void_p)
        self.handler = callback_type(lambda display, event: self.errors.append("X11 rejected a window operation") or 0)
        self.previous_handler = self.x.XSetErrorHandler(C.cast(self.handler, C.c_void_p))
        self.display = self.x.XOpenDisplay(None)
        self.held: set[str] = set()
        self.original_focus = 0
        if not self.display:
            self.x.XSetErrorHandler(self.previous_handler)
            raise RuntimeError("X11 display unavailable")
        try:
            self.root = self.x.XDefaultRootWindow(self.display)
            self.original_focus = self.focus()
            self.x.XRaiseWindow(self.display, self.window)
            self.x.XSetInputFocus(self.display, self.window, 2, 0)
            self.flush()
            self.check()
        except Exception:
            self.close()
            raise

    def flush(self) -> None:
        self.x.XSync(self.display, False)
        if self.errors:
            raise RuntimeError(self.errors[-1])

    def alive(self) -> bool:
        try:
            return process_start(self.pid) == self.identity
        except OSError:
            return False

    def focus(self) -> int:
        window, revert = C.c_ulong(), C.c_int()
        self.x.XGetInputFocus(self.display, C.byref(window), C.byref(revert))
        return window.value

    def check(self) -> None:
        if not self.display or not self.alive() or self.focus() != self.window:
            raise RuntimeError("game PID changed or game lost input focus")
        self.flush()

    def key(self, name: str, down: bool) -> None:
        if down:
            self.check()
        elif not self.alive():
            self.held.discard(name)
            return
        code = self.x.XKeysymToKeycode(self.display, self.x.XStringToKeysym(name.encode("ascii")))
        if not code:
            raise RuntimeError(f"key is unavailable: {name}")
        event = Event()
        event.key = KeyEvent(type=2 if down else 3, send_event=True, display=self.display,
                             window=self.window, root=self.root, state=1 if name == "at" or (len(name) == 1 and name.isupper()) else 0,
                             keycode=code, same_screen=True)
        if not self.x.XSendEvent(self.display, self.window, False, 1 if down else 2, C.byref(event)):
            raise RuntimeError("X11 rejected targeted keyboard input")
        (self.held.add if down else self.held.discard)(name)
        self.flush()

    def tap(self, name: str) -> None:
        self.key(name, True)
        time.sleep(.025)
        self.key(name, False)
        time.sleep(.025)

    def command(self, text: str) -> None:
        # Only the normal singleplayer setup commands used by this diagnostic.
        if text not in ("gamemode spectator", "tp @s 150.5 125 530.5 105 15", "tp @s 167.5 38 553.5 0 0", "tp @s 150.5 95 530.5 285 25", "time set 6000", "gamerule doDaylightCycle false",
                        "gamerule doWeatherCycle false", "weather clear"):
            raise ValueError("command is outside the flight fixture setup")
        self.tap("slash")
        # Chat opens in a game tick, after GLFW callbacks. Cold renderer work
        # can delay that tick; the driver still requires the exact command ack.
        time.sleep(2)
        for char in text:
            self.tap("space" if char == " " else "period" if char == "." else "at" if char == "@" else char)
        self.tap("Return")
        time.sleep(.8)

    def close(self) -> list[str]:
        if not self.display:
            return self.errors
        try:
            if self.alive():
                for name in tuple(self.held):
                    try:
                        self.key(name, False)
                    except RuntimeError as error:
                        self.errors.append(str(error))
            # Restore focus only if our window still owns it; never steal a
            # user's deliberate focus change. A vanished old window is harmless.
            if self.focus() == self.window and self.original_focus not in (0, 1):
                self.x.XSetInputFocus(self.display, self.original_focus, 2, 0)
            self.x.XSync(self.display, False)
        finally:
            try:
                self.x.XCloseDisplay(self.display)
            finally:
                self.display = None
                self.x.XSetErrorHandler(self.previous_handler)
                self.held.clear()
        return self.errors
