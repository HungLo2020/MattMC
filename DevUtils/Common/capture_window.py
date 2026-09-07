"""Shared, bounded launch dimensions for paired menu fixtures."""
import os


def menu_capture_window(title_screen_capture: bool) -> tuple[int, int]:
    width = os.environ.get("MATTMC_CAPTURE_MENU_WIDTH", "1280")
    height = os.environ.get("MATTMC_CAPTURE_MENU_HEIGHT", "720")
    if not width.isascii() or not width.isdigit() or not height.isascii() or not height.isdigit():
        raise ValueError("Menu capture dimensions must be positive decimal integers")
    size = int(width), int(height)
    if not (320 <= size[0] <= 3840 and 240 <= size[1] <= 2160):
        raise ValueError("Menu capture dimensions must be within 320x240 through 3840x2160")
    if not title_screen_capture and size != (1280, 720):
        raise ValueError("Custom capture dimensions require a title/menu fixture")
    return size
