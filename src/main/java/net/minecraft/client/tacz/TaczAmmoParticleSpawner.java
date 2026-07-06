package net.minecraft.client.tacz;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.TaczBullet;
import net.minecraft.world.phys.Vec3;

public final class TaczAmmoParticleSpawner {
	private static final Map<String, Optional<AmmoParticle>> GUN_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Optional<AmmoParticle>> AMMO_CACHE = new ConcurrentHashMap<>();

	private TaczAmmoParticleSpawner() {
	}

	public static void addParticle(TaczBullet bullet) {
		Optional<AmmoParticle> particle = GUN_CACHE.computeIfAbsent(bullet.getGunId(), TaczAmmoParticleSpawner::loadGunParticle);
		if (particle.isEmpty()) {
			particle = AMMO_CACHE.computeIfAbsent(bullet.getAmmoId(), TaczAmmoParticleSpawner::loadAmmoParticle);
		}
		particle.ifPresent(ammoParticle -> spawnParticle(bullet, ammoParticle));
	}

	private static void spawnParticle(TaczBullet bullet, AmmoParticle particle) {
		ParticleOptions particleOptions = particle.particleOptions();
		ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
		if (particle.count() == 0) {
			Particle result = particleEngine.createParticle(
				particleOptions,
				bullet.getX(),
				bullet.getY(),
				bullet.getZ(),
				particle.speed() * particle.delta().x,
				particle.speed() * particle.delta().y,
				particle.speed() * particle.delta().z
			);
			if (result != null) {
				result.setLifetime(particle.lifeTime());
			}
			return;
		}

		RandomSource random = bullet.getRandom();
		Entity owner = bullet.getOwner();
		for (int i = 0; i < particle.count(); i++) {
			createParticle(bullet, particle, random, owner, particleEngine, particleOptions);
		}
	}

	private static void createParticle(
		TaczBullet bullet, AmmoParticle particle, RandomSource random, Entity owner, ParticleEngine particleEngine, ParticleOptions particleOptions
	) {
		Vec3 deltaMovement = bullet.getDeltaMovement();
		double deltaMovementRandom = random.nextDouble();
		double offsetX = random.nextGaussian() * particle.delta().x + deltaMovementRandom * deltaMovement.x;
		double offsetY = random.nextGaussian() * particle.delta().y + deltaMovementRandom * deltaMovement.y;
		double offsetZ = random.nextGaussian() * particle.delta().z + deltaMovementRandom * deltaMovement.z;
		double posX = bullet.getX() + offsetX;
		double posY = bullet.getY() + offsetY;
		double posZ = bullet.getZ() + offsetZ;
		if (owner != null && owner.distanceToSqr(posX, posY, posZ) <= 9.0) {
			return;
		}

		Particle result = particleEngine.createParticle(
			particleOptions,
			posX,
			posY,
			posZ,
			random.nextGaussian() * particle.speed(),
			random.nextGaussian() * particle.speed(),
			random.nextGaussian() * particle.speed()
		);
		if (result != null) {
			result.setLifetime(particle.lifeTime());
		}
	}

	private static Optional<AmmoParticle> loadGunParticle(String gunId) {
		return loadParticle("assets/minecraft/display/guns/" + idPath(gunId) + "_display.json");
	}

	private static Optional<AmmoParticle> loadAmmoParticle(String ammoId) {
		return loadParticle("assets/minecraft/display/ammo/" + idPath(ammoId) + "_display.json");
	}

	private static Optional<AmmoParticle> loadParticle(String path) {
		try (InputStream stream = TaczAmmoParticleSpawner.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return Optional.empty();
			}
			JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			reader.setStrictness(Strictness.LENIENT);
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (!root.has("particle") || !root.get("particle").isJsonObject()) {
				return Optional.empty();
			}
			return parseParticle(root.getAsJsonObject("particle"));
		} catch (IOException | RuntimeException exception) {
			return Optional.empty();
		}
	}

	private static Optional<AmmoParticle> parseParticle(JsonObject jsonObject) {
		if (!jsonObject.has("name")) {
			return Optional.empty();
		}
		ParticleOptions particleOptions = parseParticleOptions(jsonObject.get("name").getAsString());
		if (particleOptions == null) {
			return Optional.empty();
		}
		Vec3 delta = parseDelta(jsonObject.getAsJsonArray("delta"));
		float speed = jsonObject.has("speed") ? jsonObject.get("speed").getAsFloat() : 0.0F;
		int lifeTime = jsonObject.has("life_time") ? Math.max(1, jsonObject.get("life_time").getAsInt()) : 1;
		int count = jsonObject.has("count") ? Math.max(0, jsonObject.get("count").getAsInt()) : 0;
		return Optional.of(new AmmoParticle(particleOptions, delta, speed, lifeTime, count));
	}

	private static ParticleOptions parseParticleOptions(String name) {
		ResourceLocation location = name.indexOf(':') >= 0 ? ResourceLocation.parse(name) : ResourceLocation.withDefaultNamespace(name);
		ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getValue(location);
		return particleType instanceof SimpleParticleType simpleParticleType ? simpleParticleType : null;
	}

	private static Vec3 parseDelta(JsonArray jsonArray) {
		if (jsonArray == null || jsonArray.size() < 3) {
			return Vec3.ZERO;
		}
		return new Vec3(jsonArray.get(0).getAsDouble(), jsonArray.get(1).getAsDouble(), jsonArray.get(2).getAsDouble());
	}

	private static String idPath(String id) {
		int separator = id.indexOf(':');
		return separator >= 0 ? id.substring(separator + 1) : id;
	}

	private record AmmoParticle(ParticleOptions particleOptions, Vec3 delta, float speed, int lifeTime, int count) {
	}
}
