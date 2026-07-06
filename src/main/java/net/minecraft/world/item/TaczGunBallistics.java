package net.minecraft.world.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

public final class TaczGunBallistics {
	private static final Map<String, GunBallistics> CACHE = new ConcurrentHashMap<>();
	private static final Map<InaccuracyType, Float> DEFAULT_INACCURACY = Map.of(
		InaccuracyType.STAND, 5.0F,
		InaccuracyType.MOVE, 5.75F,
		InaccuracyType.SNEAK, 3.5F,
		InaccuracyType.LIE, 2.5F,
		InaccuracyType.AIM, 0.15F
	);
	private static final float SCRIPT_SPREAD_TARGET_DISTANCE = 8.0F;

	private TaczGunBallistics() {
	}

	public static float inaccuracy(String gunId, TaczFireMode fireMode, LivingEntity shooter, boolean precisionAiming, float fallback) {
		GunBallistics data = data(gunId);
		InaccuracyType type = inaccuracyType(shooter, precisionAiming);
		float value = data.inaccuracy().getOrDefault(type, DEFAULT_INACCURACY.get(type));
		float adjust = data.fireModeAdjustments().getOrDefault(fireMode, FireModeAdjustment.NONE).inaccuracyAdjustment(type);
		if (!Float.isFinite(value)) {
			value = fallback;
		}
		return Math.max(value + adjust, 0.0F);
	}

	public static List<DamagePoint> damageCurve(String gunId, TaczFireMode fireMode, int bulletCount, float fallbackDamage) {
		GunBallistics data = data(gunId);
		float adjust = data.fireModeAdjustments().getOrDefault(fireMode, FireModeAdjustment.NONE).damage();
		float divisor = Math.max(1, bulletCount);
		if (data.damageCurve().isEmpty()) {
			return List.of(new DamagePoint(Float.MAX_VALUE, Math.max((fallbackDamage + adjust) / divisor, 0.0F)));
		}
		return data.damageCurve()
			.stream()
			.map(point -> new DamagePoint(point.distance(), Math.max((point.damage() + adjust) / divisor, 0.0F)))
			.toList();
	}

	public static float bulletSpeed(String gunId, TaczFireMode fireMode, float fallbackSpeed) {
		float speedAdjustMetersPerSecond = data(gunId).fireModeAdjustments().getOrDefault(fireMode, FireModeAdjustment.NONE).speed();
		return Math.max(fallbackSpeed + speedAdjustMetersPerSecond / 20.0F, 0.0F);
	}

	public static float headshotMultiplier(String gunId, TaczFireMode fireMode, float fallbackHeadshotMultiplier) {
		float adjust = data(gunId).fireModeAdjustments().getOrDefault(fireMode, FireModeAdjustment.NONE).headshotMultiplier();
		return Math.max(fallbackHeadshotMultiplier + adjust, 0.0F);
	}

	public static float knockback(String gunId, TaczFireMode fireMode, float fallbackKnockback) {
		float adjust = data(gunId).fireModeAdjustments().getOrDefault(fireMode, FireModeAdjustment.NONE).knockback();
		return Math.max(fallbackKnockback + adjust, 0.0F);
	}

	public static Optional<SpreadOffset> scriptedSpreadOffset(String gunId, int bulletIndex, float inaccuracy) {
		String script = data(gunId).script();
		if (!"tacz:sp_spread_logic".equals(script) && !"minecraft:sp_spread_logic".equals(script) && !"sp_spread_logic".equals(script)) {
			return Optional.empty();
		}
		double angle = (bulletIndex / 10.0) * Math.PI * 2.0;
		return Optional.of(new SpreadOffset(Math.cos(angle), Math.sin(angle)));
	}

	public static Vec3Like directionFromScriptedSpread(float pitch, float yaw, float velocity, SpreadOffset spreadOffset) {
		double x = spreadOffset.x();
		double y = spreadOffset.y();
		double z = SCRIPT_SPREAD_TARGET_DISTANCE;
		double pitchRadians = pitch * Mth.DEG_TO_RAD;
		double cosPitch = Math.cos(pitchRadians);
		double sinPitch = Math.sin(pitchRadians);
		double rotatedY = y * cosPitch - z * sinPitch;
		double rotatedZ = y * sinPitch + z * cosPitch;
		double yawRadians = -yaw * Mth.DEG_TO_RAD;
		double cosYaw = Math.cos(yawRadians);
		double sinYaw = Math.sin(yawRadians);
		double rotatedX = x * cosYaw + rotatedZ * sinYaw;
		double finalZ = -x * sinYaw + rotatedZ * cosYaw;
		double length = Math.sqrt(rotatedX * rotatedX + rotatedY * rotatedY + finalZ * finalZ);
		if (length <= 1.0E-7) {
			return new Vec3Like(0.0, 0.0, 0.0);
		}
		double scale = velocity / length;
		return new Vec3Like(rotatedX * scale, rotatedY * scale, finalZ * scale);
	}

	static GunBallistics dataForTest(String gunId) {
		return data(gunId);
	}

	private static GunBallistics data(String gunId) {
		return CACHE.computeIfAbsent(gunId, TaczGunBallistics::load);
	}

	private static GunBallistics load(String gunId) {
		String path = "data/minecraft/data/guns/" + gunId + "_data.json";
		try (InputStream stream = TaczGunBallistics.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return GunBallistics.EMPTY;
			}
			JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			reader.setStrictness(Strictness.LENIENT);
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return parse(root);
		} catch (IOException | RuntimeException exception) {
			return GunBallistics.EMPTY;
		}
	}

	private static GunBallistics parse(JsonObject root) {
		Map<InaccuracyType, Float> inaccuracy = new EnumMap<>(InaccuracyType.class);
		inaccuracy.putAll(DEFAULT_INACCURACY);
		if (root.has("inaccuracy") && root.get("inaccuracy").isJsonObject()) {
			JsonObject values = root.getAsJsonObject("inaccuracy");
			for (InaccuracyType type : InaccuracyType.values()) {
				if (values.has(type.serializedName())) {
					inaccuracy.put(type, values.get(type.serializedName()).getAsFloat());
				}
			}
		}

		Map<TaczFireMode, FireModeAdjustment> fireModeAdjustments = new EnumMap<>(TaczFireMode.class);
		if (root.has("fire_mode_adjust") && root.get("fire_mode_adjust").isJsonObject()) {
			JsonObject values = root.getAsJsonObject("fire_mode_adjust");
			for (TaczFireMode mode : TaczFireMode.values()) {
				if (values.has(mode.getSerializedName()) && values.get(mode.getSerializedName()).isJsonObject()) {
					fireModeAdjustments.put(mode, FireModeAdjustment.parse(values.getAsJsonObject(mode.getSerializedName())));
				}
			}
		}

		List<DamagePoint> damageCurve = parseDamageCurve(root);
		String script = root.has("script") ? root.get("script").getAsString() : "";
		return new GunBallistics(Map.copyOf(inaccuracy), Map.copyOf(fireModeAdjustments), damageCurve, script);
	}

	private static List<DamagePoint> parseDamageCurve(JsonObject root) {
		if (!root.has("bullet") || !root.get("bullet").isJsonObject()) {
			return List.of();
		}
		JsonObject bullet = root.getAsJsonObject("bullet");
		if (!bullet.has("extra_damage") || !bullet.get("extra_damage").isJsonObject()) {
			return List.of();
		}
		JsonObject extraDamage = bullet.getAsJsonObject("extra_damage");
		if (!extraDamage.has("damage_adjust") || !extraDamage.get("damage_adjust").isJsonArray()) {
			return List.of();
		}
		JsonArray values = extraDamage.getAsJsonArray("damage_adjust");
		return values.asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).map(point -> {
			JsonElement distanceElement = point.get("distance");
			float distance = distanceElement.isJsonPrimitive() && distanceElement.getAsJsonPrimitive().isString()
				? Float.MAX_VALUE
				: distanceElement.getAsFloat();
			return new DamagePoint(distance, point.get("damage").getAsFloat());
		}).toList();
	}

	private static InaccuracyType inaccuracyType(LivingEntity shooter, boolean precisionAiming) {
		if (precisionAiming) {
			return InaccuracyType.AIM;
		}
		if (!shooter.isSwimming() && shooter.getPose() == Pose.SWIMMING) {
			return InaccuracyType.LIE;
		}
		if (shooter.getPose() == Pose.CROUCHING) {
			return InaccuracyType.SNEAK;
		}
		if (shooter.walkAnimation.isMoving() || shooter.getKnownMovement().horizontalDistanceSqr() > 0.0025) {
			return InaccuracyType.MOVE;
		}
		return InaccuracyType.STAND;
	}

	enum InaccuracyType {
		STAND("stand"),
		MOVE("move"),
		SNEAK("sneak"),
		LIE("lie"),
		AIM("aim");

		private final String serializedName;

		InaccuracyType(String serializedName) {
			this.serializedName = serializedName;
		}

		String serializedName() {
			return this.serializedName;
		}
	}

	record GunBallistics(
		Map<InaccuracyType, Float> inaccuracy,
		Map<TaczFireMode, FireModeAdjustment> fireModeAdjustments,
		List<DamagePoint> damageCurve,
		String script
	) {
		static final GunBallistics EMPTY = new GunBallistics(DEFAULT_INACCURACY, Map.of(), List.of(), "");
	}

	private record FireModeAdjustment(float damage, float speed, float knockback, float headshotMultiplier, float aimInaccuracy, float otherInaccuracy) {
		static final FireModeAdjustment NONE = new FireModeAdjustment(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

		static FireModeAdjustment parse(JsonObject jsonObject) {
			return new FireModeAdjustment(
				getFloat(jsonObject, "damage"),
				getFloat(jsonObject, "speed"),
				getFloat(jsonObject, "knockback"),
				getFloat(jsonObject, "head_shot_multiplier"),
				getFloat(jsonObject, "aim_inaccuracy"),
				getFloat(jsonObject, "other_inaccuracy")
			);
		}

		float inaccuracyAdjustment(InaccuracyType type) {
			return type == InaccuracyType.AIM ? this.aimInaccuracy : this.otherInaccuracy;
		}

		private static float getFloat(JsonObject jsonObject, String key) {
			return jsonObject.has(key) ? jsonObject.get(key).getAsFloat() : 0.0F;
		}
	}

	public record DamagePoint(float distance, float damage) {
	}

	public record SpreadOffset(double x, double y) {
	}

	public record Vec3Like(double x, double y, double z) {
	}
}
