package net.matt.quantize.modules.biomes;

import net.matt.quantize.Quantize;
import net.matt.quantize.modules.biomes.SurfaceRulesManager;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.Map;

public class TerrablenderCompat {

    public static void setup(){
        Map<String, SurfaceRules.RuleSource> vanillaBiomeRules = SurfaceRulesManager.getOverworldRulesByBiomeForTerrablender(true);
        for(Map.Entry<String, SurfaceRules.RuleSource> entry : vanillaBiomeRules.entrySet()){
            terrablender.api.SurfaceRuleManager.addToDefaultSurfaceRulesAtStage(terrablender.api.SurfaceRuleManager.RuleCategory.OVERWORLD, terrablender.api.SurfaceRuleManager.RuleStage.BEFORE_BEDROCK, 0, entry.getValue());
        }
        Quantize.LOGGER.info("Added {} vanilla biome surface rule types via terrablender", vanillaBiomeRules.size());
        Map<String, SurfaceRules.RuleSource> moddedBiomeRules = SurfaceRulesManager.getOverworldRulesByBiomeForTerrablender(false);
        for(Map.Entry<String, SurfaceRules.RuleSource> entry : moddedBiomeRules.entrySet()){
            terrablender.api.SurfaceRuleManager.addSurfaceRules(terrablender.api.SurfaceRuleManager.RuleCategory.OVERWORLD, entry.getKey(), entry.getValue());
        }
        Quantize.LOGGER.info("Added {} modded biome surface rule types via terrablender", moddedBiomeRules.size());
    }
}
