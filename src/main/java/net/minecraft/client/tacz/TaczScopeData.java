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
import net.minecraft.world.item.TaczMvpGunItem;
import net.minecraft.world.item.TaczRefitGun;

public final class TaczScopeData {
	private static final Map<String, AttachmentDisplay> CACHE = new ConcurrentHashMap<>();
	private static final Map<String, GunDisplay> GUN_CACHE = new ConcurrentHashMap<>();

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
			GunDisplay display = gunDisplay(itemStack);
			if (display == null || display.ironZoom() <= 1.0F) {
				return baseFov;
			}

			float aim = TaczGlock17AnimationController.aimProgress(partialTick);
			float magnification = 1.0F + (display.ironZoom() - 1.0F) * aim;
			double halfRadians = Math.atan(Math.tan(Math.toRadians(baseFov) / 2.0) / magnification);
			return (float)Math.toDegrees(halfRadians * 2.0);
		}

		float aim = TaczGlock17AnimationController.aimProgress(partialTick);
		float magnification = 1.0F + (scope.zoom() - 1.0F) * aim;
		double halfRadians = Math.atan(Math.tan(Math.toRadians(baseFov) / 2.0) / magnification);
		return (float)Math.toDegrees(halfRadians * 2.0);
	}

	public static float applyItemFov(ItemStack itemStack, float baseFov, float partialTick) {
		AttachmentDisplay scope = scope(itemStack);
		if (scope == null) {
			GunDisplay display = gunDisplay(itemStack);
			if (display == null || display.zoomModelFov() <= 0.0F) {
				return baseFov;
			}
			return Mth.lerp(TaczGlock17AnimationController.aimProgress(partialTick), baseFov, display.zoomModelFov());
		}

		return Mth.lerp(TaczGlock17AnimationController.aimProgress(partialTick), baseFov, scope.modelFov());
	}

	private static GunDisplay gunDisplay(ItemStack itemStack) {
		if (!(itemStack.getItem() instanceof TaczMvpGunItem gunItem)) {
			return null;
		}
		return GUN_CACHE.computeIfAbsent(gunItem.gunId(), TaczScopeData::loadGunDisplay);
	}

	private static GunDisplay loadGunDisplay(String gunId) {
		ResourceLocation displayLocation = ResourceLocation.withDefaultNamespace("display/guns/" + gunId + "_display.json");
		try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(displayLocation)) {
			String json = stripLineComments(readAll(reader));
			JsonObject display = GsonHelper.parse(new StringReader(json));
			return new GunDisplay(GsonHelper.getAsFloat(display, "iron_zoom", 1.0F), GsonHelper.getAsFloat(display, "zoom_model_fov", -1.0F));
		} catch (Exception exception) {
			return null;
		}
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
			boolean showMount = GsonHelper.getAsBoolean(display, "show_mount", true);
			boolean showMuzzle = GsonHelper.getAsBoolean(display, "show_muzzle", false);
			String adapter = GsonHelper.getAsString(display, "adapter", "");
			float[] zoom = floatArray(display, "zoom", new float[]{1.0F});
			int[] views = intArray(display, "views", new int[]{1});
			float[] modelFov = floatArray(display, "views_fov", new float[]{GsonHelper.getAsFloat(display, "fov", 70.0F)});
			return new AttachmentDisplay(attachmentId, geometryLocation, textureLocation, scope, sight, showMount, showMuzzle, adapter, zoom, views, modelFov);
		} catch (Exception exception) {
			return null;
		}
	}

	private static float[] floatArray(JsonObject object, String key, float[] fallback) {
		JsonArray values = GsonHelper.getAsJsonArray(object, key, null);
		if (values == null || values.isEmpty()) {
			return fallback;
		}
		float[] result = new float[values.size()];
		for (int index = 0; index < values.size(); index++) {
			result[index] = GsonHelper.convertToFloat(values.get(index), key + "[" + index + "]");
		}
		return result;
	}

	private static int[] intArray(JsonObject object, String key, int[] fallback) {
		JsonArray values = GsonHelper.getAsJsonArray(object, key, null);
		if (values == null || values.isEmpty()) {
			return fallback;
		}
		int[] result = new int[values.size()];
		for (int index = 0; index < values.size(); index++) {
			result[index] = GsonHelper.convertToInt(values.get(index), key + "[" + index + "]");
		}
		return result;
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
		boolean showMount,
		boolean showMuzzle,
		String adapter,
		float[] zoomValues,
		int[] viewValues,
		float[] modelFovValues
	) {
		public float zoom() {
			return this.zoomValues.length == 0 ? 1.0F : this.zoomValues[0];
		}

		public float modelFov() {
			return this.modelFovValues.length == 0 ? 70.0F : this.modelFovValues[0];
		}

		public int view() {
			return this.viewValues.length == 0 ? 1 : Math.max(1, this.viewValues[0]);
		}

		public String scopeViewNodeName() {
			int view = this.view();
			return view <= 1 ? "scope_view" : "scope_view_" + view;
		}
	}

	private record GunDisplay(float ironZoom, float zoomModelFov) {
	}
}
