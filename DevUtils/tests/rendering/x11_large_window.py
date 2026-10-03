"""Bounded, temporary size requests on an already verified isolated X11 window.

An override-redirect window avoids the desktop's monitor-sized clamp without
changing display modes, opening another window or replacing the game's context.
The caller must verify exact PID/window ownership before each request.
"""
import ctypes
import ctypes.util
import re
import subprocess
import time


class WindowAttributes(ctypes.Structure):
    _fields_ = [
        ('background_pixmap', ctypes.c_ulong), ('background_pixel', ctypes.c_ulong),
        ('border_pixmap', ctypes.c_ulong), ('border_pixel', ctypes.c_ulong),
        ('bit_gravity', ctypes.c_int), ('win_gravity', ctypes.c_int),
        ('backing_store', ctypes.c_int), ('backing_planes', ctypes.c_ulong),
        ('backing_pixel', ctypes.c_ulong), ('save_under', ctypes.c_int),
        ('event_mask', ctypes.c_long), ('do_not_propagate_mask', ctypes.c_long),
        ('override_redirect', ctypes.c_int), ('colormap', ctypes.c_ulong),
        ('cursor', ctypes.c_ulong),
    ]


def request_window(target: str, extent: tuple[int, int], *, unmanaged: bool) -> None:
    if not 320 <= extent[0] <= 3840 or not 240 <= extent[1] <= 2160:
        raise ValueError('window extent must be within 320x240 through 3840x2160')
    x11 = ctypes.CDLL(ctypes.util.find_library('X11'))
    display_type, window_type = ctypes.c_void_p, ctypes.c_ulong
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = display_type
    x11.XDefaultRootWindow.argtypes = [display_type]
    x11.XDefaultRootWindow.restype = window_type
    x11.XUnmapWindow.argtypes = [display_type, window_type]
    x11.XChangeWindowAttributes.argtypes = [display_type, window_type, ctypes.c_ulong,
                                           ctypes.POINTER(WindowAttributes)]
    x11.XReparentWindow.argtypes = [display_type, window_type, window_type,
                                   ctypes.c_int, ctypes.c_int]
    x11.XResizeWindow.argtypes = [display_type, window_type, ctypes.c_uint, ctypes.c_uint]
    x11.XMapWindow.argtypes = [display_type, window_type]
    x11.XSetInputFocus.argtypes = [display_type, window_type, ctypes.c_int, ctypes.c_ulong]
    x11.XSync.argtypes = [display_type, ctypes.c_int]
    x11.XCloseDisplay.argtypes = [display_type]
    x11.XSetErrorHandler.argtypes = [ctypes.c_void_p]
    x11.XSetErrorHandler.restype = ctypes.c_void_p
    errors = []
    callback_type = ctypes.CFUNCTYPE(ctypes.c_int, display_type, ctypes.c_void_p)
    handler = callback_type(lambda display, event: errors.append(True) or 0)
    previous_handler = x11.XSetErrorHandler(ctypes.cast(handler, ctypes.c_void_p))
    display = x11.XOpenDisplay(None)
    if not display:
        x11.XSetErrorHandler(previous_handler)
        raise RuntimeError('no X11 display')
    try:
        window = int(target, 16)
        x11.XUnmapWindow(display, window)
        x11.XSync(display, False)
        if unmanaged:
            # XSync only drains our connection. Wait for the manager to finish
            # withdrawing its frame before reparenting/remapping the client.
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline:
                props = subprocess.run(['xprop', '-root', '_NET_CLIENT_LIST_STACKING'],
                    check=True, capture_output=True, text=True, timeout=2).stdout
                if window not in {int(value, 16) for value in re.findall(r'0x[0-9a-fA-F]+', props)}:
                    break
                time.sleep(.02)
            else:
                raise RuntimeError('window manager did not finish withdrawing the isolated window')
        attributes = WindowAttributes(override_redirect=int(unmanaged))
        x11.XChangeWindowAttributes(display, window, 1 << 9, ctypes.byref(attributes))
        if unmanaged:
            x11.XReparentWindow(display, window, x11.XDefaultRootWindow(display), 0, 0)
        x11.XResizeWindow(display, window, *extent)
        x11.XMapWindow(display, window)
        if unmanaged:
            x11.XSetInputFocus(display, window, 2, 0)
        x11.XSync(display, False)
        if errors:
            raise RuntimeError('X11 rejected the isolated window transition')
    finally:
        x11.XCloseDisplay(display)
        x11.XSetErrorHandler(previous_handler)
