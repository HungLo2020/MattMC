# Trader Llama

A **Trader Llama** is the merchant-associated Llama type. It shares the ordinary [Llama's](Llama.md) food, taming, strength-based cargo, decoration, and caravan behavior, but it has extra defensive targets and a **timed despawn rule**. Tame one if you want to keep it permanently under normal gameplay conditions. [Trader implementation][trader-llama] · [Shared Llama behavior][llama]

## Finding and keeping one

The type is actively registered as `minecraft:trader_llama`. Successful [Wandering Trader](WanderingTrader.md) events attempt to place **two** Trader Llamas nearby and leash them to the merchant; placement failures can leave fewer. This custom spawning system is installed for the Overworld and dispatched by its server chunk tick. It is not a normal wild-biome Llama entry. [Registration][entities] · [Merchant and Llama placement][merchant-spawner] · [Overworld spawner installation][server] · [Custom-spawner dispatch][chunk-cache] · [Spawner loop][server-level]

The event depends on normal mob spawning and `doTraderSpawning`, which defaults to enabled, as well as player, random, biome, and placement checks. The bundled exclusion tag contains The Void. This is not a guaranteed schedule for getting two new Llamas. The [Trader Llama Spawn Egg](../items/TraderLlamaSpawnEgg.md) is a Creative alternative. [Outer mob-spawning gate][chunk-cache] · [Trader gate and attempts][merchant-spawner] · [Default rule][game-rules] · [Excluded biome tag][merchant-biomes]

You cannot mount a Trader Llama while it is still leashed to a Wandering Trader. To keep one without attacking the merchant:

1. Use usable Shears on the Llama to remove its leash connections
2. Mount the adult with an empty hand and repeat after bucking until hearts signal taming
3. After taming, add a Chest or Carpet using the [Llama equipment instructions](Llama.md#chest-capacity-and-carpets)

The current mount check is specifically about a Wandering Trader being the leash holder. Shears remove leash connections before equipment, and the Lead interaction can replace a non-player leash holder. Attaching your own Lead is another way to separate it from the merchant and pause its timer, but release that Lead before mounting. Normal Llama taming uses a maximum temper of 30; Wheat and Hay Bales can help. [Mount restriction][trader-llama] · [Leash interaction order][entity] · [Taming limit and feeding][llama] · [Actual taming goal][taming]

## The timed despawn rule

A new Trader Llama starts with **47,999 game ticks**, roughly **40 minutes** at the ordinary rate. While still attached to a Wandering Trader, its checked delay tracks the merchant's remaining delay minus one; once detached, it decrements its own remaining delay. Trading can pause the merchant's countdown, so this is not an unconditional real-time lifetime. The remaining delay is saved. [Llama timer and persistence][trader-llama] · [Merchant timer][merchant]

The Llama's timer does **not advance** while any of these apply:

- It is tamed
- It is leashed to something other than a Wandering Trader, such as the player or a fence knot
- It has exactly one player passenger

Leashing or riding pauses the condition; it does not reset the saved delay. Taming provides the ordinary lasting solution. **A name alone does not stop this timer**: the custom check does not consult a name or the generic persistence flag. Releasing an untamed Llama and leaving it unoccupied can let its remaining delay run out. Simply following a caravan is not being leashed and does not pause this timer. [Exact despawn conditions][trader-llama] · [Name-tag effects][name-tag]

Timer expiry removes its leash and discards the entity. That is not the normal death-loot path, so do not rely on timed disappearance to produce the drops listed below. These rules also matter for untamed Trader Llamas created by eggs or breeding. [Expiry action and default timer][trader-llama]

## Cargo, decoration, and behavior

A tame Trader Llama accepts one ordinary Chest for **3, 6, 9, 12, or 15 cargo slots**, depending on strength. It can wear a wool Carpet and join a Llama caravan, but ordinary Saddles and Horse Armor do not fit. Mounting does not provide player steering. Follow the [Llama guide](Llama.md) for the shared food table, inventory access, cargo recovery, and caravan limits. [Strength and cargo][llama] · [Chest interaction][chested] · [Carpet component][equip] · [Saddle restriction][saddle-tag] · [Armor restriction][armor-tag]

The default merchant cloth is a rendered appearance, not an ordinary Carpet item in the inventory. A separately equipped Carpet is real recoverable equipment and replaces that appearance while worn. Removing it does not create a second merchant-cloth item. [Decoration selection][decor-renderer] · [Equipped decoration][equip]

Trader Llamas can defend their current Wandering Trader leash holder against an attacker. They also register targets for Zombies and Illagers, in addition to the ordinary Llama's retaliation and untamed-Wolf behavior. Taming does not remove those registered target goals. Their spit has **1 point of base damage** before defenses. [Extra target goals][trader-llama] · [Inherited targets][llama] · [Spit damage][spit]

## Breeding

Use two tame, full-health adults, with neither carrying a rider nor riding another entity, and feed each a Hay Bale. The usual Llama growth and cooldown rules apply. **Two Trader Llamas produce a Trader Llama**, which starts untamed and retains this type's timed-despawn behavior. Do not assume a bred baby has become an ordinary Llama that can be left untamed indefinitely. [Parent conditions][shared] · [Food and inherited breeding][llama] · [Trader offspring factory and timer][trader-llama] · [Birth and cooldown][animal]

An ordinary Llama can select a Trader Llama as a partner and produces an **ordinary Llama** in that mixed pairing. The reverse search uses the Trader Llama class and does not select an ordinary Llama, so the ordinary parent's factory determines this normal AI result. This is source-reviewed behavior; no mixed-pair breeding test was run. [Partner-class search][breed-goal] · [Subclass matching][class-filter] · [Ordinary offspring factory][llama] · [Trader offspring factory][trader-llama]

## Drops and saved items

Normal adult death loot is **0–2 Leather**, with a Looting count bonus and the usual mob-loot rule; babies do not produce that base loot. Attached Chests and ordinary cargo use the separate container-drop path. Equipped Carpets follow equipment-loot gates and drop-prevention effects. The default trader appearance adds no Carpet to the loot table. Use the [Llama recovery guidance](Llama.md#persistence-and-drops) for retrieving cargo and decoration safely. [Leather loot][trader-loot] · [Loot gate and death dispatch][living] · [Cargo drops][shared] · [Chest drops][chested] · [Equipment drops][mob]

Tame state, owner, strength, coat, equipment, Chest contents, and the trader despawn delay are saved. The timer can still resume after reloading if an untamed animal no longer meets a pause condition. [Trader save data and checks][trader-llama] · [Llama save data][llama] · [Shared state][shared] · [Cargo state][chested]

## Related pages

- [Llama](Llama.md)
- [Wandering Trader](WanderingTrader.md)
- [Trader Llama Spawn Egg](../items/TraderLlamaSpawnEgg.md)
- [Camel](Camel.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Merchant spawning and current tick dispatch, mount/leash interactions, custom despawn conditions, inherited cargo and food, target goals, offspring factories, loot, and save data were inspected. No in-game merchant, despawn, taming, caravan, or breeding test was run.

[trader-llama]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/TraderLlama.java
[llama]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/Llama.java
[entities]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java
[merchant-spawner]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/WanderingTraderSpawner.java
[server]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java
[chunk-cache]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerChunkCache.java
[server-level]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/level/ServerLevel.java
[game-rules]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/GameRules.java
[merchant-biomes]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/worldgen/biome/without_wandering_trader_spawns.json
[entity]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Entity.java
[taming]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/RunAroundLikeCrazyGoal.java
[merchant]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java
[name-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/NameTagItem.java
[chested]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractChestedHorse.java
[equip]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/equipment/Equippable.java
[saddle-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_equip_saddle.json
[armor-tag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/entity_type/can_wear_horse_armor.json
[decor-renderer]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/client/renderer/entity/layers/LlamaDecorLayer.java
[spit]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/projectile/LlamaSpit.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java
[animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Animal.java
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java
[class-filter]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/entity/EntityTypeTest.java
[trader-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/entities/trader_llama.json
[living]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
