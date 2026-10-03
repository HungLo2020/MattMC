# Giant

The **Giant** (`minecraft:giant`) is a huge, zombie-shaped mob suited to command-created displays. It belongs to the monster category, but a default Giant has **no installed wandering, targeting or attack goals**. Do not expect a normal Zombie fight just because it is listed among aggressive mobs. [Registration][entity] · [Giant behavior][giant] · [Shared goal setup][goals]

## Obtaining

There is **no dedicated Giant spawn egg** in the current item registry. The **Giant Squid Spawn Egg** creates a different mob. Picking a Giant also has no dedicated egg to return. No Giant entry was found in the checked bundled biome or structure spawn lists, including optional data packs. Commands, custom data-driven content or another system must explicitly create it; its registered ground-spawn check does not add it to natural spawn lists. [Egg registrations][items] · [Egg mapping][egg-map] · [Pick behavior][pick] · [Bundled data][data] · [Spawn selection][spawn-selection] · [Placement check][placement]

With command permission level **2** and difficulty set to **Easy, Normal or Hard**, use:

```mcfunction
/summon minecraft:giant ~ ~ ~
```

Choose an open site with room for a body **3.6 blocks wide and 12 blocks tall**. The command rejects Giants on Peaceful. For a display that should survive ordinary distance despawning, use:

```mcfunction
/summon minecraft:giant ~ ~ ~ {PersistenceRequired:1b}
```

Persistence does not protect it from damage or Peaceful removal. [Command permission and arguments][summon-args] · [Creation and Peaceful rejection][summon-create] · [Dimensions][entity] · [Persistence data][mob-data] · [Removal checks][despawn]

## Behavior

### Movement and combat

A default Giant has **100 health points (50 hearts)**. Its movement-speed attribute is **0.5** and its attack-damage attribute is **50**, but those values do not make it walk toward or attack a player. The inherited AI ticks empty goal selectors; neither Giant nor its shared parents installs the wandering, retaliation or melee goals needed for a normal encounter. Ordinary contact uses the shared push response. [Attributes][giant] · [Attribute registration][attribute-binding] · [Attribute loading][attribute-load] · [Shared parents][pathfinder] · [Monster tick][monster-tick] · [AI dispatch][ai-dispatch] · [Contact response][push]

The attack value is used if the shared attack method is actually called; it is **not guaranteed contact damage**. Equipping a weapon does not install an attack goal. A custom spawning or behavior system can change these defaults. [Attack method][attack] · [Melee-goal caller][melee-goal] · [Goal setup][goals]

Its Zombie appearance does not give it the Zombie's sunlight-burning routine. However, it still **prevents nearby non-Creative players from sleeping** through the shared monster check. Place a display away from beds. [Giant inheritance][giant] · [Zombie sunlight routine][zombie-sun] · [Monster rest rule][monster-rest] · [Bed safety check][sleep]

### Keeping and interacting with a Giant

A plain summon is not automatically persistent. A successfully applied, renamed [Name Tag](../items/NameTag.md) sets persistence and prevents ordinary distance/idle despawning. **Switching to Peaceful still removes a named or persistent Giant**, because that check runs first. [Name Tag effect][name-tag] · [Removal order][despawn]

There is no species-specific feeding, taming, breeding or right-click riding action, and an ordinary [Lead](../items/Lead.md) cannot attach to it. A default Giant neither picks up dropped equipment nor accepts dispenser equipment: those routes require its normally false `CanPickUpLoot` flag. Plain spawn initialization does not supply armor or a weapon. [Interaction handling][interactions] · [Lead restriction][leash] · [Default data][mob-data] · [Equipment gates][pickup] · [Spawn initialization][finalize]

## Drops

The bundled Giant loot table has **no loot pools**, so it provides no ordinary item drops, including Rotten Flesh. Equipment added separately can still use the shared equipment-drop rules; replacing the loot table with a data pack can also change the result. [Default table][loot] · [Empty-pool interpretation][loot-default] · [Table identity][loot-identity] · [Death dispatch][death] · [Equipment drops][equipment-drops]

An unequipped Giant has a **base reward of 5 experience**, paid under the normal recent-player-credit and `doMobLoot` checks. Custom equipment or applicable experience modifiers can alter the amount. [Monster reward][monster-tick] · [Experience calculation][xp] · [Experience gate][death] · [Experience modifiers][xp-modifier]

## Notes

The Giant uses the Zombie texture, and its renderer includes held-item and armor layers. The current world-rendering path submits its body and equipment layers through shared native collection. Equipped-Giant appearance has **not been tested in game** for this guide; check a chosen display in the current client before relying on its appearance. [Appearance and layers][renderer] · [World submission][world-rendering] · [Semantic dispatch][semantic-dispatch] · [Body and layer submission][body-and-layers] · [Native collection][native-collection]

Related: [Mobs](Mobs.md) · [Commands](../commands/Commands.md) · [Zombie](Zombie.md)

## Sources and verification

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. Checked entity/item registration, shared goal and combat dispatch, spawn selection, persistence/interactions, default loot/experience and current rendering dispatch. Resource checks covered filenames, entity IDs and namespaces across the bundled data and optional packs, together with their loading paths. [Bundled pack source][packs] · [World-data loading][world-load] · [Loot loading][loot-load] · [Death-table caller][loot-call]

**No in-game spawning, movement, combat, interaction, despawning, drop or rendering tests were run.** Commands, custom data and server settings can change the behavior described here.

[entity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L681-L684
[giant]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Giant.java#L10-L27
[goals]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L141-L165
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1877-L1887
[egg-map]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L38-L46
[pick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1370-L1375
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L123
[summon-args]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/commands/SummonCommand.java#L35-L78
[summon-create]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/commands/SummonCommand.java#L83-L116
[attribute-binding]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L173
[attribute-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[pathfinder]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/PathfinderMob.java#L13-L34
[monster-tick]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Monster.java#L31-L54
[ai-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L633-L675
[push]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2974-L2976
[attack]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1305
[melee-goal]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java#L127-L133
[sleep]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1185-L1199
[monster-rest]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Monster.java#L135-L137
[zombie-sun]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L228-L254
[name-tag]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/NameTagItem.java#L17-L33
[despawn]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[mob-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L378-L394
[leash]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1220
[interactions]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[pickup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1035-L1046
[finalize]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1010-L1024
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/entities/giant.json#L1-L4
[loot-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/storage/loot/LootTable.java#L38-L46
[loot-identity]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[death]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[xp]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L289-L307
[xp-modifier]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L586
[renderer]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/GiantMobRenderer.java#L15-L36
[packs]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/packs/repository/ServerPacksSource.java#L35-L53
[world-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[loot-load]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-call]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[world-rendering]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L784-L792
[semantic-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/EntityRenderDispatcher.java#L173-L187
[body-and-layers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/entity/LivingEntityRenderer.java#L1049-L1075
[native-collection]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L283-L303
