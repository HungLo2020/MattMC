#!/usr/bin/env python3

import sys
from pathlib import Path

from PIL import Image

####################
# To run:
# python3 DevUtils/GrayscalePNGConverter/converter.py
####################

def is_grayscale(img: Image.Image) -> bool:
	"""
	Check if an RGBA image is grayscale.
	We ignore fully transparent pixels when checking.
	"""
	rgba = img.convert("RGBA")
	for r, g, b, a in rgba.getdata():
       		# fully transparent, doesn't matter
		if a == 0:
			continue
		if r != g or g != b:
			return False
	return True


def convert_to_grayscale_in_place(path: Path) -> None:
	img = Image.open(path).convert("RGBA")
	r, g, b, a = img.split()

	# Convert RGB to luminance (grayscale)
	rgb = Image.merge("RGB", (r, g, b))
	l = rgb.convert("L")

	# Build RGBA grayscale image: same L in R/G/B, original alpha
	gray_rgba = Image.merge("RGBA", (l, l, l, a))

	gray_rgba.save(path, format="PNG")
	print(f"Converted (in-place): {path.name}")


def main() -> int:
	script_path = Path(__file__).resolve()
	script_dir = script_path.parent

	print(f"Scanning directory: {script_dir}")

	png_files = list(script_dir.glob("*.png"))
	if not png_files:
		print("No PNG files found in this directory.")
		return 0

	for path in png_files:
		try:
			img = Image.open(path).convert("RGBA")
		except Exception as e:
			print(f"Skipping (failed to open): {path.name} ({e})")
			continue

		if is_grayscale(img):
			print(f"Skipping (already grayscale): {path.name}")
			continue

		convert_to_grayscale_in_place(path)

	print("Done.")
	return 0


if __name__ == "__main__":
	sys.exit(main())
