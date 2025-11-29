package net.matt.quantize.modules.biomes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.ImmutableList;
import net.matt.quantize.Quantize;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;

public class SurfaceRulesManager {
    private static final List<SurfaceRules.RuleSource> OVERWORLD_REGISTRY = new ArrayList();
    private static final List<SurfaceRules.RuleSource> NETHER_REGISTRY = new ArrayList();
    private static final List<SurfaceRules.RuleSource> END_REGISTRY = new ArrayList();
    private static final List<SurfaceRules.RuleSource> CAVE_REGISTRY = new ArrayList();

    public SurfaceRulesManager() {
    }

    public static void registerOverworldSurfaceRule(SurfaceRules.ConditionSource condition, SurfaceRules.RuleSource rule) {
        registerOverworldSurfaceRule(SurfaceRules.ifTrue(condition, rule));
    }

    public static void registerOverworldSurfaceRule(SurfaceRules.RuleSource rule) {
        OVERWORLD_REGISTRY.add(rule);
    }

    public static void registerNetherSurfaceRule(SurfaceRules.ConditionSource condition, SurfaceRules.RuleSource rule) {
        registerNetherSurfaceRule(SurfaceRules.ifTrue(condition, rule));
    }

    public static void registerNetherSurfaceRule(SurfaceRules.RuleSource rule) {
        NETHER_REGISTRY.add(rule);
    }

    public static void registerEndSurfaceRule(SurfaceRules.ConditionSource condition, SurfaceRules.RuleSource rule) {
        registerEndSurfaceRule(SurfaceRules.ifTrue(condition, rule));
    }

    public static void registerEndSurfaceRule(SurfaceRules.RuleSource rule) {
        END_REGISTRY.add(rule);
    }

    public static void registerCaveSurfaceRule(SurfaceRules.ConditionSource condition, SurfaceRules.RuleSource rule) {
        registerCaveSurfaceRule(SurfaceRules.ifTrue(condition, rule));
    }

    public static void registerCaveSurfaceRule(SurfaceRules.RuleSource rule) {
        CAVE_REGISTRY.add(rule);
    }

    private static SurfaceRules.RuleSource mergeRules(SurfaceRules.RuleSource prev, List<SurfaceRules.RuleSource> toMerge) {
        ImmutableList.Builder<SurfaceRules.RuleSource> builder = ImmutableList.builder();
        builder.addAll(toMerge);
        builder.add(prev);
        return SurfaceRules.sequence(builder.build().toArray((size) -> new SurfaceRules.RuleSource[size]));
    }


    public static SurfaceRules.RuleSource replaceRulesOf(Holder<NoiseGeneratorSettings> holder, LevelAccessor level) {
        List<SurfaceRules.RuleSource> replaceWith = OVERWORLD_REGISTRY;
        //TODO
        return replaceWith == null ? holder.value().surfaceRule() : mergeRules(holder.get().surfaceRule(), replaceWith);
    }

    public static Map<String, SurfaceRules.RuleSource> getOverworldRulesByBiomeForTerrablender(boolean vanilla) {
        Map<String, SurfaceRules.RuleSource> map = new HashMap<>();
        for (SurfaceRules.RuleSource ruleSource : OVERWORLD_REGISTRY) {
            if (ruleSource instanceof SurfaceRules.TestRuleSource testRuleSource && testRuleSource.ifTrue() instanceof SurfaceRules.BiomeConditionSource biomeRule && !biomeRule.biomes.isEmpty()) {
                String namespace = biomeRule.biomes.get(0).location().getNamespace();
                boolean vanillaBiome = namespace.equals("minecraft");

                if (vanilla && vanillaBiome) {
                    map.put(namespace, testRuleSource);
                }
                if (!vanilla && !vanillaBiome) {
                    if (map.containsKey(namespace)) {
                        SurfaceRules.RuleSource ruleSource1 = map.get(namespace);
                        if (ruleSource1 instanceof SurfaceRules.SequenceRuleSource sequenceRuleSource) {
                            ImmutableList.Builder<SurfaceRules.RuleSource> ruleSources = ImmutableList.builder();
                            ruleSources.addAll(sequenceRuleSource.sequence());
                            ruleSources.add(testRuleSource);
                            map.put(namespace, SurfaceRules.sequence(ruleSources.build().toArray(SurfaceRules.RuleSource[]::new)));
                        } else {
                            map.put(namespace, SurfaceRules.sequence(ruleSource1, testRuleSource));
                        }
                    } else {
                        map.put(namespace, testRuleSource);
                    }
                }
            }
        }
        return map;
    }
    public static boolean hasOverworldModifications(){
        return !OVERWORLD_REGISTRY.isEmpty();
    }

    public static SurfaceRules.RuleSource mergeOverworldRules(SurfaceRules.RuleSource rulesIn) {
        Quantize.LOGGER.info("merged {} surface rules with vanilla rule {}", OVERWORLD_REGISTRY.size(), rulesIn.getClass().getSimpleName());
        return mergeRules(rulesIn, List.of(SurfaceRules.sequence(OVERWORLD_REGISTRY.toArray(SurfaceRules.RuleSource[]::new))));
    }
}