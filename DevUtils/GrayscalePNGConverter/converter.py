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


def convert_to_grayscale_in_place(path: Path, normalize_to_white: bool = True) -> None:
	"""
	Convert a colored image to grayscale suitable for color tinting.
	
	Uses the maximum RGB channel value instead of luminance to preserve
	brightness. When normalize_to_white=True, scales the brightest pixel
	to 255 so the image can be fully tinted to any color.
	
	This produces bright grayscale images that, when multiplied by a tint
	color in the shader (fragColor = texColor * vColor), give the expected
	vibrant result instead of a dark/muddy one.
	"""
	img = Image.open(path).convert("RGBA")
	pixels = list(img.getdata())
	
	# Use max channel value for each pixel (preserves brightness better than luminance)
	gray_values = []
	max_gray = 0
	for r, g, b, a in pixels:
		if a > 0:
			gray = max(r, g, b)
			max_gray = max(max_gray, gray)
		else:
			gray = 0
		gray_values.append((gray, a))
	
	# Normalize to white if requested (makes brightest pixel = 255)
	if normalize_to_white and max_gray > 0 and max_gray < 255:
		scale = 255.0 / max_gray
		gray_values = [(min(255, int(g * scale)), a) for g, a in gray_values]
	
	# Build new RGBA image with grayscale values
	new_pixels = [(g, g, g, a) for g, a in gray_values]
	gray_rgba = Image.new("RGBA", img.size)
	gray_rgba.putdata(new_pixels)

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
