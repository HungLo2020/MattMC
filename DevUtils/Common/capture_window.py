"""Shared, bounded launch dimensions for paired world and menu fixtures."""
import os


def _dimensions(prefix: str, label: str) -> tuple[int, int]:
    width = os.environ.get(f"{prefix}_WIDTH", "1280")
    height = os.environ.get(f"{prefix}_HEIGHT", "720")
    if not width.isascii() or not width.isdigit() or not height.isascii() or not height.isdigit():
        raise ValueError(f"{label} capture dimensions must be positive decimal integers")
    size = int(width), int(height)
    if not (320 <= size[0] <= 3840 and 240 <= size[1] <= 2160):
        raise ValueError(f"{label} capture dimensions must be within 320x240 through 3840x2160")
    return size


def menu_capture_window(title_screen_capture: bool) -> tuple[int, int]:
    size = _dimensions("MATTMC_CAPTURE_MENU", "Menu")
    if not title_screen_capture and size != (1280, 720):
        raise ValueError("Custom capture dimensions require a title/menu fixture")
    return size


def capture_window_size(title_screen_capture: bool) -> tuple[int, int]:
    menu = menu_capture_window(title_screen_capture)
    return menu if title_screen_capture else _dimensions("MATTMC_CAPTURE_WORLD", "World")
