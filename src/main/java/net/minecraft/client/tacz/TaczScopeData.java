package net.minecraft.client.tacz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczAttachmentItem;
import net.minecraft.world.item.TaczAttachmentType;
import net.minecraft.world.item.TaczRefitGun;

public final class TaczScopeData {
	private static final Map<String, AttachmentDisplay> CACHE = new ConcurrentHashMap<>();

	private TaczScopeData() {
	}

	public static AttachmentDisplay scope(ItemStack gunStack) {
		ItemStack scopeStack = TaczRefitGun.getStoredAttachment(gunStack, TaczAttachmentType.SCOPE);
		if (!(scopeStack.getItem() instanceof TaczAttachmentItem attachment)) {
			return null;
		}

		AttachmentDisplay display = display(attachment.getAttachmentId());
		return display != null && (display.scope() || display.sight()) ? display : null;
	}

	public static AttachmentDisplay display(String attachmentId) {
		if (attachmentId == null || attachmentId.isEmpty()) {
			return null;
		}
		return CACHE.computeIfAbsent(attachmentId, TaczScopeData::loadDisplay);
	}

	public static float applyWorldFov(ItemStack itemStack, float baseFov, float partialTick) {
		AttachmentDisplay scope = scope(itemStack);
		if (scope == null || scope.zoom() <= 1.0F) {
			return baseFov;
		}

		float aim = TaczGlock17AnimationController.aimProgress(partialTick);
		float magnification = 1.0F + (scope.zoom() - 1.0F) * aim;
		double halfRadians = Math.atan(Math.tan(Math.toRadians(baseFov) / 2.0) / magnification);
		return (float)Math.toDegrees(halfRadians * 2.0);
	}

	public static float applyItemFov(ItemStack itemStack, float baseFov, float partialTick) {
		AttachmentDisplay scope = scope(itemStack);
		if (scope == null) {
			return baseFov;
		}

		return Mth.lerp(TaczGlock17AnimationController.aimProgress(partialTick), baseFov, scope.modelFov());
	}

	private static AttachmentDisplay loadDisplay(String attachmentId) {
		ResourceLocation displayLocation = ResourceLocation.withDefaultNamespace("display/attachments/" + attachmentId + "_display.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(displayLocation)) {
			String json = stripLineComments(readAll(reader));
			JsonObject display = GsonHelper.parse(new StringReader(json));
			ResourceLocation model = ResourceLocation.parse(GsonHelper.getAsString(display, "model"));
			ResourceLocation texture = ResourceLocation.parse(GsonHelper.getAsString(display, "texture"));
			ResourceLocation geometryLocation = ResourceLocation.fromNamespaceAndPath(model.getNamespace(), "geo_models/" + model.getPath() + ".json");
			ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath() + ".png");
			boolean scope = GsonHelper.getAsBoolean(display, "scope", false);
			boolean sight = GsonHelper.getAsBoolean(display, "sight", false);
			float zoom = firstFloat(display, "zoom", 1.0F);
			float modelFov = firstFloat(display, "views_fov", GsonHelper.getAsFloat(display, "fov", 70.0F));
			return new AttachmentDisplay(attachmentId, geometryLocation, textureLocation, scope, sight, zoom, modelFov);
		} catch (Exception exception) {
			return null;
		}
	}

	private static float firstFloat(JsonObject object, String key, float fallback) {
		JsonArray values = GsonHelper.getAsJsonArray(object, key, null);
		if (values == null || values.isEmpty()) {
			return fallback;
		}
		return GsonHelper.convertToFloat(values.get(0), key + "[0]");
	}

	private static String readAll(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[2048];
		int read;
		while ((read = reader.read(buffer)) >= 0) {
			builder.append(buffer, 0, read);
		}
		return builder.toString();
	}

	private static String stripLineComments(String text) {
		return text.replaceAll("(?m)//.*$", "");
	}

	public record AttachmentDisplay(
		String id,
		ResourceLocation geometryLocation,
		ResourceLocation textureLocation,
		boolean scope,
		boolean sight,
		float zoom,
		float modelFov
	) {
	}
}
