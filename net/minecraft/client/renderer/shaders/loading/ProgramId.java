// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.loading;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifies all shader programs in a shader pack with fallback chains.
 * 
 * COPIED VERBATIM from IRIS's ProgramId.java (adapted for MattMC).
 * Reference: frnsrc/Iris-1.21.9/.../shaderpack/loading/ProgramId.java
 * 
 * Step 15 of NEW-SHADER-PLAN.md
 */
public enum ProgramId {
	// Shadow programs
	Shadow(ProgramGroup.Shadow, ""),
	ShadowSolid(ProgramGroup.Shadow, "solid", Shadow),
	ShadowCutout(ProgramGroup.Shadow, "cutout", Shadow),
	ShadowWater(ProgramGroup.Shadow, "water", Shadow),
	ShadowEntities(ProgramGroup.Shadow, "entities", Shadow),
	ShadowLightning(ProgramGroup.Shadow, "lightning", ShadowEntities),
	ShadowBlock(ProgramGroup.Shadow, "block", Shadow),

	// Gbuffers programs - Basic tier
	Basic(ProgramGroup.Gbuffers, "basic"),
	Line(ProgramGroup.Gbuffers, "line", Basic),

	// Gbuffers programs - Textured tier
	Textured(ProgramGroup.Gbuffers, "textured", Basic),
	TexturedLit(ProgramGroup.Gbuffers, "textured_lit", Textured),
	SkyBasic(ProgramGroup.Gbuffers, "skybasic", Basic),
	SkyTextured(ProgramGroup.Gbuffers, "skytextured", Textured),
	Clouds(ProgramGroup.Gbuffers, "clouds", Textured),

	// Gbuffers programs - Terrain tier
	Terrain(ProgramGroup.Gbuffers, "terrain", TexturedLit),
	TerrainSolid(ProgramGroup.Gbuffers, "terrain_solid", Terrain),
	TerrainCutout(ProgramGroup.Gbuffers, "terrain_cutout", Terrain),
	DamagedBlock(ProgramGroup.Gbuffers, "damagedblock", Terrain),

	// Gbuffers programs - Block tier
	Block(ProgramGroup.Gbuffers, "block", Terrain),
	BlockTrans(ProgramGroup.Gbuffers, "block_translucent", Block),
	BeaconBeam(ProgramGroup.Gbuffers, "beaconbeam", Textured),
	Item(ProgramGroup.Gbuffers, "item", TexturedLit),

	// Gbuffers programs - Entities tier
	Entities(ProgramGroup.Gbuffers, "entities", TexturedLit),
	EntitiesTrans(ProgramGroup.Gbuffers, "entities_translucent", Entities),
	Lightning(ProgramGroup.Gbuffers, "lightning", Entities),
	Particles(ProgramGroup.Gbuffers, "particles", TexturedLit),
	ParticlesTrans(ProgramGroup.Gbuffers, "particles_translucent", Particles),
	EntitiesGlowing(ProgramGroup.Gbuffers, "entities_glowing", Entities),
	ArmorGlint(ProgramGroup.Gbuffers, "armor_glint", Textured),
	SpiderEyes(ProgramGroup.Gbuffers, "spidereyes", Textured),

	// Gbuffers programs - Hand and Water
	Hand(ProgramGroup.Gbuffers, "hand", TexturedLit),
	Weather(ProgramGroup.Gbuffers, "weather", TexturedLit),
	Water(ProgramGroup.Gbuffers, "water", Terrain),
	HandWater(ProgramGroup.Gbuffers, "hand_water", Hand),
	
	// Distant Horizons programs
	DhTerrain(ProgramGroup.Dh, "terrain"),
	DhWater(ProgramGroup.Dh, "water", DhTerrain),
	DhGeneric(ProgramGroup.Dh, "generic", DhTerrain),
	DhShadow(ProgramGroup.Dh, "shadow"),

	// Final program
	Final(ProgramGroup.Final, ""),
	;

	private final ProgramGroup group;
	private final String sourceName;
	private final ProgramId fallback;

	ProgramId(ProgramGroup group, String name) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = null;
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback) {
		this.group = group;
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = Objects.requireNonNull(fallback);
	}

	public ProgramGroup getGroup() {
		return group;
	}

	public String getSourceName() {
		return sourceName;
	}

	public Optional<ProgramId> getFallback() {
		return Optional.ofNullable(fallback);
	}
}
