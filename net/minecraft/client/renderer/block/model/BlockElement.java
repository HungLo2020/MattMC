package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public record BlockElement(
	Vector3fc from, Vector3fc to, Map<Direction, BlockElementFace> faces, @Nullable BlockElementRotation rotation, boolean shade, int lightEmission
) {
	private static final boolean DEFAULT_RESCALE = false;
	private static final float MIN_EXTENT = -16.0F;
	private static final float MAX_EXTENT = 32.0F;

	public BlockElement(Vector3fc vector3fc, Vector3fc vector3fc2, Map<Direction, BlockElementFace> map) {
		this(vector3fc, vector3fc2, map, null, true, 0);
	}

	@Environment(EnvType.CLIENT)
	protected static class Deserializer implements JsonDeserializer<BlockElement> {
		private static final boolean DEFAULT_SHADE = true;
		private static final int DEFAULT_LIGHT_EMISSION = 0;

		public BlockElement deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			Vector3f vector3f = this.getFrom(jsonObject);
			Vector3f vector3f2 = this.getTo(jsonObject);
			BlockElementRotation blockElementRotation = this.getRotation(jsonObject);
			Map<Direction, BlockElementFace> map = this.getFaces(jsonDeserializationContext, jsonObject);
			if (jsonObject.has("shade") && !GsonHelper.isBooleanValue(jsonObject, "shade")) {
				throw new JsonParseException("Expected shade to be a Boolean");
			} else {
				boolean bl = GsonHelper.getAsBoolean(jsonObject, "shade", true);
				int i = 0;
				if (jsonObject.has("light_emission")) {
					boolean bl2 = GsonHelper.isNumberValue(jsonObject, "light_emission");
					if (bl2) {
						i = GsonHelper.getAsInt(jsonObject, "light_emission");
					}

					if (!bl2 || i < 0 || i > 15) {
						throw new JsonParseException("Expected light_emission to be an Integer between (inclusive) 0 and 15");
					}
				}

				return new BlockElement(vector3f, vector3f2, map, blockElementRotation, bl, i);
			}
		}

		@Nullable
		private BlockElementRotation getRotation(JsonObject jsonObject) {
			BlockElementRotation blockElementRotation = null;
			if (jsonObject.has("rotation")) {
				JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "rotation");
				Vector3f vector3f = this.getVector3f(jsonObject2, "origin");
				vector3f.mul(0.0625F);
				Axis axis = this.getAxis(jsonObject2);
				float f = this.getAngle(jsonObject2);
				boolean bl = GsonHelper.getAsBoolean(jsonObject2, "rescale", false);
				blockElementRotation = new BlockElementRotation(vector3f, axis, f, bl);
			}

			return blockElementRotation;
		}

		private float getAngle(JsonObject jsonObject) {
			float f = GsonHelper.getAsFloat(jsonObject, "angle");
			if (Mth.abs(f) > 45.0F) {
				throw new JsonParseException("Invalid rotation " + f + " found, only values in [-45,45] range allowed");
			} else {
				return f;
			}
		}

		private Axis getAxis(JsonObject jsonObject) {
			String string = GsonHelper.getAsString(jsonObject, "axis");
			Axis axis = Axis.byName(string.toLowerCase(Locale.ROOT));
			if (axis == null) {
				throw new JsonParseException("Invalid rotation axis: " + string);
			} else {
				return axis;
			}
		}

		private Map<Direction, BlockElementFace> getFaces(JsonDeserializationContext jsonDeserializationContext, JsonObject jsonObject) {
			Map<Direction, BlockElementFace> map = this.filterNullFromFaces(jsonDeserializationContext, jsonObject);
			if (map.isEmpty()) {
				throw new JsonParseException("Expected between 1 and 6 unique faces, got 0");
			} else {
				return map;
			}
		}

		private Map<Direction, BlockElementFace> filterNullFromFaces(JsonDeserializationContext jsonDeserializationContext, JsonObject jsonObject) {
			Map<Direction, BlockElementFace> map = Maps.newEnumMap(Direction.class);
			JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "faces");

			for (Entry<String, JsonElement> entry : jsonObject2.entrySet()) {
				Direction direction = this.getFacing((String)entry.getKey());
				map.put(direction, (BlockElementFace)jsonDeserializationContext.deserialize((JsonElement)entry.getValue(), BlockElementFace.class));
			}

			return map;
		}

		private Direction getFacing(String string) {
			Direction direction = Direction.byName(string);
			if (direction == null) {
				throw new JsonParseException("Unknown facing: " + string);
			} else {
				return direction;
			}
		}

		private Vector3f getTo(JsonObject jsonObject) {
			Vector3f vector3f = this.getVector3f(jsonObject, "to");
			if (!(vector3f.x() < -16.0F)
				&& !(vector3f.y() < -16.0F)
				&& !(vector3f.z() < -16.0F)
				&& !(vector3f.x() > 32.0F)
				&& !(vector3f.y() > 32.0F)
				&& !(vector3f.z() > 32.0F)) {
				return vector3f;
			} else {
				throw new JsonParseException("'to' specifier exceeds the allowed boundaries: " + vector3f);
			}
		}

		private Vector3f getFrom(JsonObject jsonObject) {
			Vector3f vector3f = this.getVector3f(jsonObject, "from");
			if (!(vector3f.x() < -16.0F)
				&& !(vector3f.y() < -16.0F)
				&& !(vector3f.z() < -16.0F)
				&& !(vector3f.x() > 32.0F)
				&& !(vector3f.y() > 32.0F)
				&& !(vector3f.z() > 32.0F)) {
				return vector3f;
			} else {
				throw new JsonParseException("'from' specifier exceeds the allowed boundaries: " + vector3f);
			}
		}

		private Vector3f getVector3f(JsonObject jsonObject, String string) {
			JsonArray jsonArray = GsonHelper.getAsJsonArray(jsonObject, string);
			if (jsonArray.size() != 3) {
				throw new JsonParseException("Expected 3 " + string + " values, found: " + jsonArray.size());
			} else {
				float[] fs = new float[3];

				for (int i = 0; i < fs.length; i++) {
					fs[i] = GsonHelper.convertToFloat(jsonArray.get(i), string + "[" + i + "]");
				}

				return new Vector3f(fs[0], fs[1], fs[2]);
			}
		}
	}
}
