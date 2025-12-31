package net.minecraft.util;

import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ARGB {
	public static int alpha(int i) {
		return i >>> 24;
	}

	public static int red(int i) {
		return i >> 16 & 0xFF;
	}

	public static int green(int i) {
		return i >> 8 & 0xFF;
	}

	public static int blue(int i) {
		return i & 0xFF;
	}

	public static int color(int i, int j, int k, int l) {
		return (i & 0xFF) << 24 | (j & 0xFF) << 16 | (k & 0xFF) << 8 | l & 0xFF;
	}

	public static int color(int i, int j, int k) {
		return color(255, i, j, k);
	}

	public static int color(Vec3 vec3) {
		return color(as8BitChannel((float)vec3.x()), as8BitChannel((float)vec3.y()), as8BitChannel((float)vec3.z()));
	}

	public static int multiply(int i, int j) {
		if (i == -1) {
			return j;
		} else {
			return j == -1 ? i : color(alpha(i) * alpha(j) / 255, red(i) * red(j) / 255, green(i) * green(j) / 255, blue(i) * blue(j) / 255);
		}
	}

	public static int scaleRGB(int i, float f) {
		return scaleRGB(i, f, f, f);
	}

	public static int scaleRGB(int i, float f, float g, float h) {
		return color(alpha(i), Math.clamp((int)(red(i) * f), 0, 255), Math.clamp((int)(green(i) * g), 0, 255), Math.clamp((int)(blue(i) * h), 0, 255));
	}

	public static int scaleRGB(int i, int j) {
		return color(
			alpha(i), Math.clamp((long)red(i) * j / 255L, 0, 255), Math.clamp((long)green(i) * j / 255L, 0, 255), Math.clamp((long)blue(i) * j / 255L, 0, 255)
		);
	}

	public static int greyscale(int i) {
		int j = (int)(red(i) * 0.3F + green(i) * 0.59F + blue(i) * 0.11F);
		return color(j, j, j);
	}

	public static int lerp(float f, int i, int j) {
		int k = Mth.lerpInt(f, alpha(i), alpha(j));
		int l = Mth.lerpInt(f, red(i), red(j));
		int m = Mth.lerpInt(f, green(i), green(j));
		int n = Mth.lerpInt(f, blue(i), blue(j));
		return color(k, l, m, n);
	}

	public static int opaque(int i) {
		return i | 0xFF000000;
	}

	public static int transparent(int i) {
		return i & 16777215;
	}

	public static int color(int i, int j) {
		return i << 24 | j & 16777215;
	}

	public static int color(float f, int i) {
		return as8BitChannel(f) << 24 | i & 16777215;
	}

	public static int white(float f) {
		return as8BitChannel(f) << 24 | 16777215;
	}

	public static int colorFromFloat(float f, float g, float h, float i) {
		return color(as8BitChannel(f), as8BitChannel(g), as8BitChannel(h), as8BitChannel(i));
	}

	public static Vector3f vector3fFromRGB24(int i) {
		float f = red(i) / 255.0F;
		float g = green(i) / 255.0F;
		float h = blue(i) / 255.0F;
		return new Vector3f(f, g, h);
	}

	public static int average(int i, int j) {
		return color((alpha(i) + alpha(j)) / 2, (red(i) + red(j)) / 2, (green(i) + green(j)) / 2, (blue(i) + blue(j)) / 2);
	}

	public static int as8BitChannel(float f) {
		return Mth.floor(f * 255.0F);
	}

	public static float alphaFloat(int i) {
		return from8BitChannel(alpha(i));
	}

	public static float redFloat(int i) {
		return from8BitChannel(red(i));
	}

	public static float greenFloat(int i) {
		return from8BitChannel(green(i));
	}

	public static float blueFloat(int i) {
		return from8BitChannel(blue(i));
	}

	private static float from8BitChannel(int i) {
		return i / 255.0F;
	}

	public static int toABGR(int i) {
		return i & -16711936 | (i & 0xFF0000) >> 16 | (i & 0xFF) << 16;
	}

	public static int fromABGR(int i) {
		return toABGR(i);
	}

	public static int setBrightness(int i, float f) {
		int j = red(i);
		int k = green(i);
		int l = blue(i);
		int m = alpha(i);
		int n = Math.max(Math.max(j, k), l);
		int o = Math.min(Math.min(j, k), l);
		float g = n - o;
		float h;
		if (n != 0) {
			h = g / n;
		} else {
			h = 0.0F;
		}

		float p;
		if (h == 0.0F) {
			p = 0.0F;
		} else {
			float q = (n - j) / g;
			float r = (n - k) / g;
			float s = (n - l) / g;
			if (j == n) {
				p = s - r;
			} else if (k == n) {
				p = 2.0F + q - s;
			} else {
				p = 4.0F + r - q;
			}

			p /= 6.0F;
			if (p < 0.0F) {
				p++;
			}
		}

		if (h == 0.0F) {
			j = k = l = Math.round(f * 255.0F);
			return color(m, j, k, l);
		} else {
			float qx = (p - (float)Math.floor(p)) * 6.0F;
			float rx = qx - (float)Math.floor(qx);
			float sx = f * (1.0F - h);
			float t = f * (1.0F - h * rx);
			float u = f * (1.0F - h * (1.0F - rx));
			switch ((int)qx) {
				case 0:
					j = Math.round(f * 255.0F);
					k = Math.round(u * 255.0F);
					l = Math.round(sx * 255.0F);
					break;
				case 1:
					j = Math.round(t * 255.0F);
					k = Math.round(f * 255.0F);
					l = Math.round(sx * 255.0F);
					break;
				case 2:
					j = Math.round(sx * 255.0F);
					k = Math.round(f * 255.0F);
					l = Math.round(u * 255.0F);
					break;
				case 3:
					j = Math.round(sx * 255.0F);
					k = Math.round(t * 255.0F);
					l = Math.round(f * 255.0F);
					break;
				case 4:
					j = Math.round(u * 255.0F);
					k = Math.round(sx * 255.0F);
					l = Math.round(f * 255.0F);
					break;
				case 5:
					j = Math.round(f * 255.0F);
					k = Math.round(sx * 255.0F);
					l = Math.round(t * 255.0F);
			}

			return color(m, j, k, l);
		}
	}
}
