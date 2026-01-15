package net.minecraft.client.renderer.texture;

import net.blaze3d.platform.NativeImage;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.ARGB;

@Environment(EnvType.CLIENT)
public class MipmapGenerator {
	private static final int ALPHA_CUTOUT_CUTOFF = 96;

	private MipmapGenerator() {
	}

	public static NativeImage[] generateMipLevels(NativeImage[] nativeImages, int i) {
		if (i + 1 <= nativeImages.length) {
			return nativeImages;
		} else {
			NativeImage[] nativeImages2 = new NativeImage[i + 1];
			nativeImages2[0] = nativeImages[0];
			boolean bl = hasTransparentPixel(nativeImages2[0]);

			for (int j = 1; j <= i; j++) {
				if (j < nativeImages.length) {
					nativeImages2[j] = nativeImages[j];
				} else {
					NativeImage nativeImage = nativeImages2[j - 1];
					NativeImage nativeImage2 = new NativeImage(nativeImage.getWidth() >> 1, nativeImage.getHeight() >> 1, false);
					int k = nativeImage2.getWidth();
					int l = nativeImage2.getHeight();

					for (int m = 0; m < k; m++) {
						for (int n = 0; n < l; n++) {
							nativeImage2.setPixel(
								m,
								n,
								alphaBlend(
									nativeImage.getPixel(m * 2 + 0, n * 2 + 0),
									nativeImage.getPixel(m * 2 + 1, n * 2 + 0),
									nativeImage.getPixel(m * 2 + 0, n * 2 + 1),
									nativeImage.getPixel(m * 2 + 1, n * 2 + 1),
									bl
								)
							);
						}
					}

					nativeImages2[j] = nativeImage2;
				}
			}

			return nativeImages2;
		}
	}

	private static boolean hasTransparentPixel(NativeImage nativeImage) {
		for (int i = 0; i < nativeImage.getWidth(); i++) {
			for (int j = 0; j < nativeImage.getHeight(); j++) {
				if (ARGB.alpha(nativeImage.getPixel(i, j)) == 0) {
					return true;
				}
			}
		}

		return false;
	}

	private static int alphaBlend(int one, int two, int three, int four, boolean checkAlpha) {
		// Sodium: Enhanced mipmap downsampling (from MipmapGeneratorMixin)
		// Combines linear color spaces with alpha-weighted blending for minimal visual artifacts
		// First blend horizontally, then blend vertically.
		// This works well for the case where our change is the most impactful (grass side overlays)
		return weightedAverageColor(weightedAverageColor(one, two), weightedAverageColor(three, four));
	}

	private static int weightedAverageColor(int one, int two) {
		int alphaOne = net.sodium.api.util.ColorABGR.unpackAlpha(one);
		int alphaTwo = net.sodium.api.util.ColorABGR.unpackAlpha(two);

		// In the case where the alpha values of the same, we can get by with an unweighted average.
		if (alphaOne == alphaTwo) {
			return averageRgb(one, two, alphaOne);
		}

		// If one of our pixels is fully transparent, ignore it.
		// We just take the value of the other pixel as-is. To compensate for not changing the color value, we
		// divide the alpha value by 4 instead of 2.
		if (alphaOne == 0) {
			return (two & 0x00FFFFFF) | ((alphaTwo >> 2) << 24);
		}

		if (alphaTwo == 0) {
			return (one & 0x00FFFFFF) | ((alphaOne >> 2) << 24);
		}

		// Use the alpha values to compute relative weights of each color.
		float scale = 1.0f / (alphaOne + alphaTwo);

		float relativeWeightOne = alphaOne * scale;
		float relativeWeightTwo = alphaTwo * scale;

		// Convert the color components into linear space, then multiply the corresponding weight.
		float oneR = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackRed(one)) * relativeWeightOne;
		float oneG = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackGreen(one)) * relativeWeightOne;
		float oneB = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackBlue(one)) * relativeWeightOne;

		float twoR = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackRed(two)) * relativeWeightTwo;
		float twoG = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackGreen(two)) * relativeWeightTwo;
		float twoB = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackBlue(two)) * relativeWeightTwo;

		// Combine the color components of each color
		float linearR = oneR + twoR;
		float linearG = oneG + twoG;
		float linearB = oneB + twoB;

		// Take the average alpha of both alpha values
		int averageAlpha = (alphaOne + alphaTwo) >> 1;

		// Convert to sRGB and pack the colors back into an integer.
		return net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.linearToSrgb(linearR, linearG, linearB, averageAlpha);
	}

	// Computes a non-weighted average of the two sRGB colors in linear space, avoiding brightness losses.
	private static int averageRgb(int a, int b, int alpha) {
		float ar = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackRed(a));
		float ag = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackGreen(a));
		float ab = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackBlue(a));

		float br = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackRed(b));
		float bg = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackGreen(b));
		float bb = net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.srgbToLinear(net.sodium.api.util.ColorABGR.unpackBlue(b));

		return net.caffeinemc.mods.sodium.client.util.color.ColorSRGB.linearToSrgb((ar + br) * 0.5f, (ag + bg) * 0.5f, (ab + bb) * 0.5f, alpha);
	}
}
