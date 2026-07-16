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
import java.util.ArrayList;
import java.util.Comparator;
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
	private static final Map<String, AttachmentRecoilModifiers> ATTACHMENT_CACHE = new ConcurrentHashMap<>();
	private static final Map<InaccuracyType, Float> DEFAULT_INACCURACY = Map.of(
		InaccuracyType.STAND, 5.0F,
		InaccuracyType.MOVE, 5.75F,
		InaccuracyType.SNEAK, 3.5F,
		InaccuracyType.LIE, 2.5F,
		InaccuracyType.AIM, 0.15F
	);
	private static final float SCRIPT_SPREAD_TARGET_DISTANCE = 8.0F;
	private static final float DEFAULT_CRAWL_RECOIL_MULTIPLIER = 0.5F;

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

	public static RecoilInstance recoilInstance(String gunId, ItemStack gunStack, float aimingProgress, boolean crawling, float zoom) {
		GunBallistics data = data(gunId);
		if (data.recoil().isEmpty()) {
			return RecoilInstance.EMPTY;
		}

		float aimingModifier = 1.0F - aimingProgress + aimingProgress / (float)Math.min(Math.sqrt(zoom), 1.5);
		if (crawling) {
			aimingModifier *= data.crawlRecoilMultiplier();
		}

		AttachmentRecoilModifiers attachments = installedAttachmentRecoilModifiers(gunStack);
		float pitchModifier = attachments.pitch().eval(aimingModifier);
		float yawModifier = attachments.yaw().eval(aimingModifier);
		return new RecoilInstance(data.recoil().pitch().sample(pitchModifier), data.recoil().yaw().sample(yawModifier));
	}

	public static AttachmentRecoilModifiers installedAttachmentRecoilModifiers(ItemStack gunStack) {
		if (!(gunStack.getItem() instanceof TaczRefitGun gun)) {
			return AttachmentRecoilModifiers.EMPTY;
		}

		List<Modifier> pitch = new ArrayList<>();
		List<Modifier> yaw = new ArrayList<>();
		for (TaczAttachmentType type : TaczAttachmentType.values()) {
			ItemStack attachmentStack = gun.getAttachment(gunStack, type);
			if (!(attachmentStack.getItem() instanceof TaczAttachmentItem attachment)) {
				continue;
			}
			AttachmentRecoilModifiers modifiers = attachmentRecoilModifiers(attachment.getAttachmentId());
			pitch.addAll(modifiers.pitch().modifiers());
			yaw.addAll(modifiers.yaw().modifiers());
		}
		return new AttachmentRecoilModifiers(new ParameterizedModifiers(List.copyOf(pitch)), new ParameterizedModifiers(List.copyOf(yaw)));
	}

	public static AttachmentRecoilModifiers attachmentRecoilModifiers(String attachmentId) {
		return ATTACHMENT_CACHE.computeIfAbsent(attachmentId, TaczGunBallistics::loadAttachmentRecoilModifiers);
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
		float crawlRecoilMultiplier = root.has("crawl_recoil_multiplier") ? root.get("crawl_recoil_multiplier").getAsFloat() : DEFAULT_CRAWL_RECOIL_MULTIPLIER;
		GunRecoil recoil = parseRecoil(root);
		return new GunBallistics(Map.copyOf(inaccuracy), Map.copyOf(fireModeAdjustments), damageCurve, script, crawlRecoilMultiplier, recoil);
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

	private static GunRecoil parseRecoil(JsonObject root) {
		if (!root.has("recoil") || !root.get("recoil").isJsonObject()) {
			return GunRecoil.EMPTY;
		}

		JsonObject recoil = root.getAsJsonObject("recoil");
		return new GunRecoil(parseRecoilCurve(recoil, "pitch"), parseRecoilCurve(recoil, "yaw"));
	}

	private static RecoilCurve parseRecoilCurve(JsonObject recoil, String key) {
		if (!recoil.has(key) || !recoil.get(key).isJsonArray()) {
			return RecoilCurve.EMPTY;
		}

		List<RecoilKeyFrame> keyFrames = recoil.getAsJsonArray(key)
			.asList()
			.stream()
			.filter(JsonElement::isJsonObject)
			.map(JsonElement::getAsJsonObject)
			.map(TaczGunBallistics::parseRecoilKeyFrame)
			.flatMap(Optional::stream)
			.sorted(Comparator.comparingDouble(RecoilKeyFrame::time))
			.toList();
		return new RecoilCurve(keyFrames);
	}

	private static Optional<RecoilKeyFrame> parseRecoilKeyFrame(JsonObject frame) {
		if (!frame.has("time") || !frame.has("value") || !frame.get("value").isJsonArray()) {
			return Optional.empty();
		}

		JsonArray value = frame.getAsJsonArray("value");
		if (value.size() != 2) {
			return Optional.empty();
		}

		float time = frame.get("time").getAsFloat();
		float min = value.get(0).getAsFloat();
		float max = value.get(1).getAsFloat();
		if (time < 0.0F || min > max) {
			return Optional.empty();
		}
		return Optional.of(new RecoilKeyFrame(time, min, max));
	}

	private static AttachmentRecoilModifiers loadAttachmentRecoilModifiers(String attachmentId) {
		String path = "data/minecraft/data/attachments/" + attachmentId + "_data.json";
		try (InputStream stream = TaczGunBallistics.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return AttachmentRecoilModifiers.EMPTY;
			}
			JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			reader.setStrictness(Strictness.LENIENT);
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			return parseAttachmentRecoilModifiers(root);
		} catch (IOException | RuntimeException exception) {
			return AttachmentRecoilModifiers.EMPTY;
		}
	}

	private static AttachmentRecoilModifiers parseAttachmentRecoilModifiers(JsonObject root) {
		if (!root.has("recoil") || !root.get("recoil").isJsonObject()) {
			if (!root.has("recoil_modifier") || !root.get("recoil_modifier").isJsonObject()) {
				return AttachmentRecoilModifiers.EMPTY;
			}
			JsonObject legacy = root.getAsJsonObject("recoil_modifier");
			Modifier pitch = legacy.has("pitch") ? Modifier.percent(legacy.get("pitch").getAsDouble()) : Modifier.IDENTITY;
			Modifier yaw = legacy.has("yaw") ? Modifier.percent(legacy.get("yaw").getAsDouble()) : Modifier.IDENTITY;
			return new AttachmentRecoilModifiers(new ParameterizedModifiers(List.of(pitch)), new ParameterizedModifiers(List.of(yaw)));
		}

		JsonObject recoil = root.getAsJsonObject("recoil");
		return new AttachmentRecoilModifiers(
			new ParameterizedModifiers(parseModifiers(recoil, "pitch")),
			new ParameterizedModifiers(parseModifiers(recoil, "yaw"))
		);
	}

	private static List<Modifier> parseModifiers(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) {
			return List.of();
		}

		JsonObject value = root.getAsJsonObject(key);
		return List.of(new Modifier(
			getDouble(value, "addend", 0.0),
			getDouble(value, "percent", 0.0),
			Math.max(getDouble(value, "multiplier", 1.0), 0.0),
			value.has("function") ? value.get("function").getAsString() : ""
		));
	}

	private static double getDouble(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
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
		String script,
		float crawlRecoilMultiplier,
		GunRecoil recoil
	) {
		static final GunBallistics EMPTY = new GunBallistics(DEFAULT_INACCURACY, Map.of(), List.of(), "", DEFAULT_CRAWL_RECOIL_MULTIPLIER, GunRecoil.EMPTY);
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

	public record GunRecoil(RecoilCurve pitch, RecoilCurve yaw) {
		static final GunRecoil EMPTY = new GunRecoil(RecoilCurve.EMPTY, RecoilCurve.EMPTY);

		boolean isEmpty() {
			return this.pitch.isEmpty() && this.yaw.isEmpty();
		}
	}

	public record RecoilCurve(List<RecoilKeyFrame> keyFrames) {
		static final RecoilCurve EMPTY = new RecoilCurve(List.of());

		public boolean isEmpty() {
			return this.keyFrames.isEmpty();
		}

		RecoilSpline sample(float modifier) {
			if (this.keyFrames.isEmpty()) {
				return RecoilSpline.EMPTY;
			}

			double[] times = new double[this.keyFrames.size() + 1];
			double[] values = new double[this.keyFrames.size() + 1];
			times[0] = 0.0;
			values[0] = 0.0;
			for (int i = 0; i < this.keyFrames.size(); i++) {
				RecoilKeyFrame frame = this.keyFrames.get(i);
				times[i + 1] = frame.time() * 1000.0 + 30.0;
				values[i + 1] = frame.sample() * modifier;
			}
			return RecoilSpline.interpolate(times, values);
		}
	}

	public record RecoilKeyFrame(float time, float minValue, float maxValue) {
		double sample() {
			return this.minValue + Math.random() * (this.maxValue - this.minValue);
		}
	}

	public record RecoilInstance(RecoilSpline pitch, RecoilSpline yaw) {
		static final RecoilInstance EMPTY = new RecoilInstance(RecoilSpline.EMPTY, RecoilSpline.EMPTY);
	}

	public static final class RecoilSpline {
		public static final RecoilSpline EMPTY = new RecoilSpline(new double[0], new Polynomial[0]);
		private final double[] knots;
		private final Polynomial[] polynomials;

		private RecoilSpline(double[] knots, Polynomial[] polynomials) {
			this.knots = knots;
			this.polynomials = polynomials;
		}

		static RecoilSpline interpolate(double[] x, double[] y) {
			if (x.length < 2 || x.length != y.length) {
				return EMPTY;
			}
			for (int i = 1; i < x.length; i++) {
				if (x[i] <= x[i - 1]) {
					return EMPTY;
				}
			}

			int n = x.length - 1;
			double[] h = new double[n];
			for (int i = 0; i < n; i++) {
				h[i] = x[i + 1] - x[i];
			}

			double[] mu = new double[n];
			double[] z = new double[n + 1];
			for (int i = 1; i < n; i++) {
				double g = 2.0 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1];
				mu[i] = h[i] / g;
				z[i] = (3.0 * (y[i + 1] * h[i - 1] - y[i] * (x[i + 1] - x[i - 1]) + y[i - 1] * h[i]) / (h[i - 1] * h[i]) - h[i - 1] * z[i - 1]) / g;
			}

			double[] c = new double[n + 1];
			double[] b = new double[n];
			double[] d = new double[n];
			for (int j = n - 1; j >= 0; j--) {
				c[j] = z[j] - mu[j] * c[j + 1];
				b[j] = (y[j + 1] - y[j]) / h[j] - h[j] * (c[j + 1] + 2.0 * c[j]) / 3.0;
				d[j] = (c[j + 1] - c[j]) / (3.0 * h[j]);
			}

			Polynomial[] polynomials = new Polynomial[n];
			for (int i = 0; i < n; i++) {
				polynomials[i] = new Polynomial(y[i], b[i], c[i], d[i]);
			}
			return new RecoilSpline(x.clone(), polynomials);
		}

		public boolean isValidPoint(double x) {
			return this.knots.length >= 2 && x >= this.knots[0] && x <= this.knots[this.knots.length - 1];
		}

		public double value(double x) {
			if (!this.isValidPoint(x)) {
				return 0.0;
			}
			int segment = this.polynomials.length - 1;
			for (int i = 0; i < this.polynomials.length; i++) {
				if (x <= this.knots[i + 1]) {
					segment = i;
					break;
				}
			}
			return this.polynomials[segment].value(x - this.knots[segment]);
		}

		private record Polynomial(double a, double b, double c, double d) {
			double value(double x) {
				return this.a + x * (this.b + x * (this.c + x * this.d));
			}
		}
	}

	public record AttachmentRecoilModifiers(ParameterizedModifiers pitch, ParameterizedModifiers yaw) {
		static final AttachmentRecoilModifiers EMPTY = new AttachmentRecoilModifiers(ParameterizedModifiers.EMPTY, ParameterizedModifiers.EMPTY);
	}

	public record ParameterizedModifiers(List<Modifier> modifiers) {
		static final ParameterizedModifiers EMPTY = new ParameterizedModifiers(List.of());

		float eval(double input) {
			double addend = 0.0;
			double percent = 1.0;
			double multiplier = 1.0;
			for (Modifier modifier : this.modifiers) {
				addend += modifier.addend();
				percent += modifier.percent();
				multiplier *= Math.max(modifier.multiplier(), 0.0);
			}
			double value = (input + addend) * Math.max(percent, 0.0) * multiplier;
			for (Modifier modifier : this.modifiers) {
				if (!modifier.function().isBlank()) {
					value = NativeTaczFunctionModifierEvaluator.eval(value, input, modifier.function());
				}
			}
			return (float)value;
		}
	}

	public record Modifier(double addend, double percent, double multiplier, String function) {
		static final Modifier IDENTITY = new Modifier(0.0, 0.0, 1.0, "");

		static Modifier percent(double percent) {
			return new Modifier(0.0, percent, 1.0, "");
		}
	}
}
