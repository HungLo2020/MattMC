package net.minecraft.client.gui.components;

import net.blaze3d.audio.ListenerTransform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.SoundSubtitle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the subtitles Rust records for played sounds (`audio/policy/
 * subtitles.rs` owns the entries, their refreshes and expiry).
 */
@Environment(EnvType.CLIENT)
public class SubtitleOverlay {
	private final Minecraft minecraft;
	private final Map<String, Component> texts = new HashMap<>();
	private boolean isListening;

	public SubtitleOverlay(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	private Component text(String key) {
		return this.texts.computeIfAbsent(key, k -> SharedConstants.DEBUG_SUBTITLES ? Component.literal(k) : Component.translatable(k));
	}

	public void render(GuiGraphics guiGraphics) {
		SoundManager soundManager = this.minecraft.getSoundManager();
		if (!this.isListening && this.minecraft.options.showSubtitles().get()) {
			soundManager.setSubtitlesListening(true);
			this.isListening = true;
		} else if (this.isListening && !this.minecraft.options.showSubtitles().get()) {
			soundManager.setSubtitlesListening(false);
			this.isListening = false;
		}

		if (this.isListening) {
			ListenerTransform listenerTransform = soundManager.getListenerTransform();
			Vec3 vec3 = listenerTransform.position();
			Vec3 vec32 = listenerTransform.forward();
			Vec3 vec33 = listenerTransform.right();
			double d = this.minecraft.options.notificationDisplayTime().get();
			List<SoundSubtitle> audibleSubtitles = soundManager.subtitleFrame(vec3, 3000.0 * d);
			if (!audibleSubtitles.isEmpty()) {
				int i = 0;
				int j = 0;

				for (SoundSubtitle subtitle : audibleSubtitles) {
					j = Math.max(j, this.minecraft.font.width(this.text(subtitle.text())));
				}

				j += this.minecraft.font.width("<") + this.minecraft.font.width(" ") + this.minecraft.font.width(">") + this.minecraft.font.width(" ");
				guiGraphics.nextStratum();

				for (SoundSubtitle subtitle : audibleSubtitles) {
					Component component = this.text(subtitle.text());
					Vec3 vec34 = new Vec3(subtitle.x(), subtitle.y(), subtitle.z()).subtract(vec3).normalize();
					double e = vec33.dot(vec34);
					double f = vec32.dot(vec34);
					boolean bl = f > 0.5;
					int l = j / 2;
					int m = 9;
					int n = m / 2;
					int o = this.minecraft.font.width(component);
					int p = Mth.floor(Mth.clampedLerp(255.0F, 75.0F, (float)subtitle.ageMillis() / (float)(3000.0 * d)));
					guiGraphics.pose().pushMatrix();
					guiGraphics.pose().translate(guiGraphics.guiWidth() - l * 1.0F - 2.0F, guiGraphics.guiHeight() - 35 - i * (m + 1) * 1.0F);
					guiGraphics.pose().scale(1.0F, 1.0F);
					guiGraphics.fill(-l - 1, -n - 1, l + 1, n + 1, this.minecraft.options.getBackgroundColor(0.8F));
					int q = ARGB.color(255, p, p, p);
					if (!bl) {
						if (e > 0.0) {
							guiGraphics.drawString(this.minecraft.font, ">", l - this.minecraft.font.width(">"), -n, q);
						} else if (e < 0.0) {
							guiGraphics.drawString(this.minecraft.font, "<", -l, -n, q);
						}
					}

					guiGraphics.drawString(this.minecraft.font, component, -o / 2, -n, q);
					guiGraphics.pose().popMatrix();
					i++;
				}
			}
		}
	}
}
