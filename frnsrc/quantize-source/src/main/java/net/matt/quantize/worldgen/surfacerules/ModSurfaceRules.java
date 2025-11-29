package net.matt.quantize.worldgen.surfacerules;

import net.matt.quantize.block.QBlocks;
import net.matt.quantize.modules.biomes.SurfaceRulesManager;
import net.matt.quantize.worldgen.QBiomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;

public class ModSurfaceRules {
    private static final SurfaceRules.RuleSource DIRT = makeStateRule(Blocks.DIRT);
    private static final SurfaceRules.RuleSource GRASS_BLOCK = makeStateRule(Blocks.GRASS_BLOCK);
    //private static final SurfaceRules.RuleSource JADE = makeStateRule(QBlocks.JADE_BRICKS.get());
    //private static final SurfaceRules.RuleSource RAW_SAPPHIRE = makeStateRule(QBlocks.JADE_ORE.get());



    public static void setup() {
        SurfaceRulesManager.registerOverworldSurfaceRule(SurfaceRules.isBiome(QBiomes.PRIMORDIAL_CAVES), createPrimordialCavesRules());
        SurfaceRulesManager.registerOverworldSurfaceRule(SurfaceRules.isBiome(QBiomes.BAYOU), createBayouSurfaceRules());
        SurfaceRulesManager.registerOverworldSurfaceRule(SurfaceRules.isBiome(QBiomes.LUSH_BEACH), createLushBeachSurfaceRules());
        SurfaceRulesManager.registerOverworldSurfaceRule(SurfaceRules.isBiome(QBiomes.MOJAVE_DESERT), createMojaveDesertSurfaceRules());
    }



    private static SurfaceRules.RuleSource createMojaveDesertSurfaceRules() {
        // Blocks
        SurfaceRules.RuleSource SAND      = SurfaceRules.state(Blocks.SAND.defaultBlockState());
        SurfaceRules.RuleSource SANDSTONE = SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState());

        // Top: sand
        SurfaceRules.RuleSource top = SAND;

        // Subsurface: sand, then sandstone deeper
        SurfaceRules.RuleSource subsurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SAND),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, SANDSTONE)
        );

        // Apply only to the Mojave Desert and only near the surface
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(QBiomes.MOJAVE_DESERT),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, top),
                                        subsurface
                                )
                        )
                )
        );
    }

    private static SurfaceRules.RuleSource createLushBeachSurfaceRules() {
        // Blocks
        SurfaceRules.RuleSource SAND       = SurfaceRules.state(Blocks.SAND.defaultBlockState());
        SurfaceRules.RuleSource SANDSTONE  = SurfaceRules.state(Blocks.SANDSTONE.defaultBlockState());

        // “Dry at surface” check (vanilla uses this to put grass when dry; we’ll still place sand)
        SurfaceRules.ConditionSource waterAtSurface = SurfaceRules.waterBlockCheck(0, 0);

        // Top layer: always sand (the water check is basically a no-op here but mirrors vanilla style)
        SurfaceRules.RuleSource top = SurfaceRules.sequence(
                SurfaceRules.ifTrue(waterAtSurface, SAND),
                SAND
        );

        // Subsurface: a bit of sand, then sandstone deeper down
        SurfaceRules.RuleSource subsurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SAND),
                SurfaceRules.ifTrue(SurfaceRules.DEEP_UNDER_FLOOR, SANDSTONE)
        );

        // Apply only for LUSH_BEACH and only around the preliminary surface
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(
                        SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.ifTrue(
                                SurfaceRules.isBiome(QBiomes.LUSH_BEACH),
                                SurfaceRules.sequence(
                                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, top),
                                        subsurface
                                )
                        )
                )
        );
    }

    private static SurfaceRules.RuleSource createBayouSurfaceRules() {
        // --- Block states ---
        SurfaceRules.RuleSource GRASS = SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState());
        SurfaceRules.RuleSource DIRT  = SurfaceRules.state(Blocks.DIRT.defaultBlockState());
        SurfaceRules.RuleSource MUD   = SurfaceRules.state(Blocks.MUD.defaultBlockState());
        SurfaceRules.RuleSource WATER = SurfaceRules.state(Blocks.WATER.defaultBlockState());

        // --- Common conditions (mirroring RU style) ---
        // Below sea level (use 63 or 64 depending on how you tuned sea level visuals; RU uses 64)
        SurfaceRules.ConditionSource belowSea = SurfaceRules.not(
                SurfaceRules.yStartCheck(VerticalAnchor.absolute(64), 0)
        );

        // Checks if the column is "dry" at the surface position (used by RU to pick grass vs dirt)
        SurfaceRules.ConditionSource waterCheckSurface = SurfaceRules.waterBlockCheck(0, 0);
        // One block lower (handy if you want to place puddles on the floor)
        SurfaceRules.ConditionSource waterCheckBelow   = SurfaceRules.waterBlockCheck(-1, 0);

        // Swamp noise gates (choose thresholds to taste)
        // RU often uses a single-sided check like noiseCondition(Noises.SWAMP, 0.0D)
        SurfaceRules.ConditionSource swampNoiseMud = SurfaceRules.noiseCondition(Noises.SWAMP, 0.0D);
        // Slightly stronger gate if you want rarer puddles
        SurfaceRules.ConditionSource swampNoisePuddle = SurfaceRules.noiseCondition(Noises.SWAMP, 0.35D);

        // --- Vanilla-style top surface fallback (grass over dirt when dry) ---
        SurfaceRules.RuleSource GrassSurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(waterCheckSurface, GRASS),
                DIRT
        );

        // --- Bayou: mud patches below sea level driven by SWAMP noise (RU style) ---
        SurfaceRules.RuleSource BayouMudPatches = SurfaceRules.ifTrue(
                SurfaceRules.isBiome(QBiomes.BAYOU),
                SurfaceRules.ifTrue(
                        belowSea,
                        SurfaceRules.ifTrue(swampNoiseMud, MUD)
                )
        );

        // --- Optional: tiny puddles (place water on the floor when below sea & strong swamp noise) ---
        // Comment this block out if you don’t want surface rules placing water.
        SurfaceRules.RuleSource BayouPuddles = SurfaceRules.ifTrue(
                SurfaceRules.isBiome(QBiomes.BAYOU),
                SurfaceRules.ifTrue(
                        SurfaceRules.ON_FLOOR,
                        // both conditions must be true:
                        SurfaceRules.ifTrue(
                                belowSea,
                                SurfaceRules.ifTrue(swampNoisePuddle, WATER)
                        )
                )
        );

        // --- Build the on-floor behavior like RU: bayou-specific tweaks first, then generic grass/dirt ---
        SurfaceRules.RuleSource onFloor = SurfaceRules.sequence(
                // Bayou-specific: mud patches
                BayouMudPatches,
                // Bayou-specific: optional puddles
                BayouPuddles,
                // Fallback everywhere: grass over dirt when “dry”, otherwise dirt
                GrassSurface
        );

        // RU wraps its main “on floor / under floor” logic with abovePreliminarySurface().
        // Keep it simple here: just apply our rules on the floor, then let vanilla handle depths.
        SurfaceRules.RuleSource buildSurface = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, onFloor)
                // You can add UNDER_FLOOR tweaks for Bayou here if you want sub-surface mud:
                // SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, SurfaceRules.ifTrue(SurfaceRules.isBiome(QBiomes.BAYOU), MUD))
        );

        // Final sequence, matching RU’s pattern of gating by abovePreliminarySurface
        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), buildSurface)
        );
    }

    public static SurfaceRules.RuleSource createPrimordialCavesRules() {
        SurfaceRules.RuleSource limestone = SurfaceRules.state(QBlocks.LIMESTONE.get().defaultBlockState());
        SurfaceRules.RuleSource grass = SurfaceRules.state(Blocks.GRASS_BLOCK.defaultBlockState());
        SurfaceRules.RuleSource dirt = SurfaceRules.state(Blocks.DIRT.defaultBlockState());
        SurfaceRules.RuleSource packedMud = SurfaceRules.state(Blocks.PACKED_MUD.defaultBlockState());
        SurfaceRules.RuleSource dirtOrPackedMud = SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.GRAVEL, -0.12D, 0.2D), packedMud), dirt);
        SurfaceRules.ConditionSource isUnderwater = SurfaceRules.waterBlockCheck(0, 0);
        SurfaceRules.RuleSource grassWaterChecked = SurfaceRules.sequence(SurfaceRules.ifTrue(isUnderwater, grass), dirtOrPackedMud);
        SurfaceRules.RuleSource floorRules = SurfaceRules.sequence(SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, grassWaterChecked), SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, dirtOrPackedMud));
        return SurfaceRules.sequence(bedrock(), floorRules, createBands(15, 1, 20, Blocks.SANDSTONE.defaultBlockState()), limestone);
    }

    private static SurfaceRules.RuleSource bedrock() {
        SurfaceRules.RuleSource bedrock = SurfaceRules.state(Blocks.BEDROCK.defaultBlockState());
        SurfaceRules.ConditionSource bedrockCondition = SurfaceRules.verticalGradient("bedrock", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5));
        return SurfaceRules.ifTrue(bedrockCondition, bedrock);
    }

    private static SurfaceRules.RuleSource createBands(int layers, int layerThickness, int layerDistance, BlockState state) {
        SurfaceRules.RuleSource sandstone = SurfaceRules.state(state);
        SurfaceRules.RuleSource[] ruleSources = new SurfaceRules.RuleSource[layers];
        for (int i = 1; i <= layers; i++) {
            int yDown = i * layerDistance;
            int extra = i % 3 == 0 ? 1 : 0;
            SurfaceRules.ConditionSource layer1 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(62 - yDown), 0);
            SurfaceRules.ConditionSource layer2 = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(62 + extra + layerThickness - yDown), 0);
            ruleSources[i - 1] = SurfaceRules.ifTrue(layer1, SurfaceRules.ifTrue(SurfaceRules.not(layer2), SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.ICE, -0.7D, 0.8D), sandstone)));
        }
        return SurfaceRules.sequence(ruleSources);
    }




    private static SurfaceRules.RuleSource makeStateRule(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }
}