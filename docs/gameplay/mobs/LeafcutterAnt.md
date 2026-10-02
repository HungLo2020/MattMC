# Leafcutter Ant

The **Leafcutter Ant** is a neutral mob in MattMC.

## Obtaining

Leafcutter Ant can be encountered through its normal spawning rules, structures, events, or content-specific systems when those systems are available. For creative testing and mapmaking, use the [Leafcutter Ant Spawn Egg](../items/LeafcutterAntSpawnEgg.md).

## Behavior

Leafcutter Ant is classified as neutral here: it is not treated as always hostile, but source behavior shows it can defend itself, retaliate, guard allies, or attack under specific conditions.

## Nests and Pupa use

See [Leafcutter ant nests](../blocks/LeafcutterNests.md) for the Anthill, Chamber, and Pupa interaction. The checked anthill implementation does not complete home discovery, ant storage, or fungus production; placing nest blocks beside ants does not establish a working colony. The guide distinguishes those limits from the active Pupa-spawning action.

## Notes

* This mob is registered as `minecraft:leafcutter_ant`.
* Its spawn egg is registered as `minecraft:leafcutter_ant_spawn_egg`.
* Its entity registration uses `MobCategory.CREATURE`.
* Its entity class is `EntityLeafcutterAnt`.
* Its registered size is `0.7, 0.5` blocks.
* This mob comes from bundled Alex's Mobs content integrated into MattMC.
