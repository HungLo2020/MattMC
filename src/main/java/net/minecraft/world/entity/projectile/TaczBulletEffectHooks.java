package net.minecraft.world.entity.projectile;

import java.util.function.Consumer;

public final class TaczBulletEffectHooks {
	private static Consumer<TaczBullet> ammoParticleSpawner = bullet -> {
	};

	private TaczBulletEffectHooks() {
	}

	public static void setAmmoParticleSpawner(Consumer<TaczBullet> spawner) {
		ammoParticleSpawner = spawner;
	}

	public static void addAmmoParticle(TaczBullet bullet) {
		ammoParticleSpawner.accept(bullet);
	}
}
