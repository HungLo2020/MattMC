package com.github.alexthe666.alexsmobs.config;

import com.google.common.collect.Lists;
import java.util.List;

public class AMConfig {
    // Catfish spawn configuration
    public static int catfishSpawnWeight = 4;
    public static int catfishSpawnRolls = 2;
    
    // Comb Jelly spawn configuration
    public static int combJellySpawnRolls = 2;
    
    // Crow spawn configuration
    public static int crowSpawnRolls = 2;
    public static boolean crowsStealCrops = true;
    
    // Endergrade spawn configuration
    public static int endergradeSpawnRolls = 2;
    
    // Hummingbird spawn configuration
    public static int hummingbirdSpawnRolls = 2;
    
    // Mimic Octopus spawn configuration
    public static int mimicOctopusSpawnRolls = 2;
    
    // Mudskipper spawn configuration
    public static int mudskipperSpawnRolls = 2;
    
    // Seagull spawn configuration
    public static int seagullSpawnWeight = 21;
    public static int seagullSpawnRolls = 0;
    public static boolean seagullStealing = true;
    
    // Shoebill spawn configuration
    public static int shoebillSpawnRolls = 0;
    
    // Spectre spawn configuration
    public static int spectreSpawnRolls = 2;
    
    // Toucan spawn configuration
    public static int toucanSpawnWeight = 23;
    public static int toucanSpawnRolls = 0;
    public static List<? extends String> toucanFruitMatches = Lists.newArrayList(
            "minecraft:apple|minecraft:oak_sapling",
            "minecraft:golden_apple|minecraft:oak_sapling",
            "minecraft:enchanted_golden_apple|minecraft:oak_sapling"
    );
    
    // Anaconda spawn configuration
    public static int anacondaSpawnRolls = 2;
    
    // Anteater spawn configuration
    public static int anteaterSpawnRolls = 2;
    
    // Bison spawn configuration
    public static int bisonSpawnWeight = 15;
    
    // Cosmaw spawn configuration
    public static int cosmawSpawnRolls = 2;
    
    // Elephant spawn configuration
    public static int elephantSpawnRolls = 2;
    
    // Gelada Monkey spawn configuration
    public static int geladaMonkeySpawnRolls = 2;
    
    // Giant Squid spawn configuration
    public static int giantSquidSpawnRolls = 2;
    
    // Rattlesnake spawn configuration
    public static int rattlesnakeSpawnRolls = 2;
    
    // Tarantula Hawk spawn configuration
    public static int tarantulaHawkSpawnRolls = 2;
    public static boolean fireproofTarantulaHawk = false;
    
    // Underminer spawn configuration
    public static int underminerSpawnWeight = 50;
    public static int underminerSpawnRolls = 1;
    public static double underminerDisappearDistance = 8.0;
    
    // Rhinoceros spawn configuration
    public static int rhinocerosSpawnRolls = 2;
    
    // Skunk spawn configuration
    public static int skunkSpawnRolls = 2;
    
    // Komodo Dragon spawn configuration
    public static int komodoDragonSpawnRolls = 2;
}
