# Emu

Emu is a large bird integrated from Alex's Mobs. Adults lay throwable eggs, accept the chicken-food seed tag for breeding, and can fight or panic when threatened. Do not treat one as harmless merely because it is a farmable animal.

## At a glance

- Entity ID: `minecraft:emu`
- Health: **20 points** (10 hearts)
- Registered size: **0.9 blocks wide × 2 blocks tall**
- Base movement-speed attribute: **0.35**, not a blocks-per-second value
- Base attack-damage attribute: **3 points**, before combat conditions and modifiers

## Obtaining and breeding

Creative lists the [Emu Spawn Egg](../items/EmuSpawnEgg.md). A thrown [Emu Egg](../items/EmuEgg.md) has a separate chance to create babies. Natural spawning is not established by the checked biome and spawn-placement registrations; the class's bright-ground test is not proof of a working plains or savanna spawn route.

Emus are attracted to and accept the bundled chicken-food tag: **Wheat Seeds, Melon Seeds, Pumpkin Seeds, Beetroot Seeds, Torchflower Seeds, and Pitcher Pods**. Ready adults use the normal breeding goal, and their offspring method creates another Emu, inheriting the calling parent's variant. Babies can follow their parents.

No taming or mounting interaction is established in this class. Breeding is different from gaining ownership.

## Collecting eggs

A living adult lays one [Emu Egg](../items/EmuEgg.md) after its timer counts down, then resets the timer to **6,000–11,999 ticks**. That is roughly **5–10 minutes** at 20 ticks per second while the bird is ticking. The remaining timer is saved with the animal; unloaded time does not count down.

Eggs can be thrown and sometimes produce babies. They are not placed nest blocks. The separately registered [Boiled Emu Egg](../items/BoiledEmuEgg.md) has food values, but no cooking recipe was found in the active data reviewed here.

## Behavior and care

The active goal list includes herd panic, melee pursuit, and retaliation. It also targets skeleton-type mobs and pillagers. Keep an enclosure separated from those enemies rather than assuming the bird will ignore them.

Damage can trigger short panic/retaliation cooldowns among nearby emus. Babies are prevented from attacking by the class's attack check. Exact combat animation integration and damage delivery have not been tested in a running world.

No dedicated Emu death-loot table was found in the bundled entity loot directory. The verified renewable resource described here is the adult's periodic egg.

## Related pages

- [Emu Egg](../items/EmuEgg.md)
- [Boiled Emu Egg](../items/BoiledEmuEgg.md)
- [Roadrunner](Roadrunner.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game spawning, breeding, combat, egg-throwing, or food test was run.

- [Emu implementation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityEmu.java)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L504-L510)
- [Active attributes](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L157)
- [Accepted breeding foods](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/chicken_food.json)
- [Spawn-placement review](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)
- [Egg projectile](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityEmuEgg.java)
