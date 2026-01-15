package net.distanthorizons.common.wrappers.block;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import net.distanthorizons.core.util.ColorUtil;
import net.minecraft.client.renderer.texture.SpriteContents;

/**
 * For wrapping/utilizing around TextureAtlasSprite
 *
 * @author Ran
 */
public class TextureAtlasSpriteWrapper
{
	public static int getPixelRGBA(TextureAtlasSprite sprite, int frameIndex, int x, int y)
	{
		
		SpriteContents content = sprite.contents(); // don't close, otherwise MC will be corrupted and you won't be able to re-access the texture
		if (content.animatedTexture != null)
		{
			x += content.animatedTexture.getFrameX(frameIndex) * content.width();
			y += content.animatedTexture.getFrameY(frameIndex) * content.width();
		}
		
		int abgr = content.originalImage.getPixel(x, y);
		// re-pack the color so we can access it normally
		int a = (abgr & 0xFF000000) >>> 24;
		int b = (abgr & 0x00FF0000) >>> 16;
		int g = (abgr & 0x0000FF00) >>> 8;
		int r = (abgr & 0x000000FF);
		return ColorUtil.argbToInt(a, r, g, b);
		
	}
	
}
