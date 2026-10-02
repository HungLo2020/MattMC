# Raid

A raid sends successive groups of hostile raiders toward a recognized village. Prepare the villagers' shelter, your recovery supplies and a route around the settlement before deliberately drinking an [Ominous Bottle](../items/OminousBottle.md). The bottle starts an effect chain; simply carrying it or fighting a patrol does not start that chain. [Bottle consumption][bottle-consume] · [Bad Omen application][bottle-effect]

## Starting or avoiding a raid

1. **Drink an Ominous Bottle.** It applies Bad Omen I–V for 120,000 ticks, normally 100 minutes at 20 TPS. A non-raid [Pillager captain](../mobs/Pillager.md#captains-and-ominous-bottles) is one source; [Vaults](../blocks/Vault.md#normal-versus-ominous-rewards) have separate reward routes. [Bottle level/duration][bottle-effect]
2. **Enter a recognized village with Bad Omen.** A non-spectator server player outside Peaceful receives Raid Omen of the same level for **600 ticks**, nominally 30 seconds, and Bad Omen is removed. An existing nearby raid already at level V prevents this conversion. [Village conversion][bad-omen] · [Effect removal return][effect-tick]
3. **Use the countdown to cancel if needed.** Finish drinking [Milk](../items/MilkBucket.md) before Raid Omen's final effect tick to remove it. Milk also removes other effects. Walking out of the village is not a reliable cancellation: the effect stores the position where conversion happened, and its final tick uses that saved position rather than requiring you to stand there again. [Final-tick start][raid-omen] · [Milk effect][milk] · [Clear-all dispatch][clear-effects]
4. **When Raid Omen expires, the server creates or extends the raid.** Creation checks spectator status, `disableRaids` and the dimension's raid permission. The raid absorbs the effect level, capped at V. A new raid then has its own 300-tick preparation countdown before its first wave. [Creation and extension][raid-create] · [Level absorption][raid-absorb] · [Initial preparation][raid-initial] · [Countdown][raid-tick]

**A generated village building is not the trigger by itself.** The village check uses occupied village points of interest and nearby sections. These points include homes, meeting places and acquirable job sites. A player-built settlement can therefore qualify, while an abandoned-looking group of houses is not sufficient evidence. The initial raid center averages occupied village points within 64 blocks of the saved omen position, falling back to that position if none are found. [Village distance][village-check] · [Occupied points][village-poi] · [Point types][village-tag] · [Center choice][raid-create]

Bad Omen also has a separate [Trial Spawner conversion](../blocks/TrialSpawner.md#becoming-ominous). Plan which encounter you want before drinking; the Trial Spawner guide owns Trial Omen and ominous-trial rewards.

## Waves and difficulty

| Difficulty when the raid is created | Base waves | With raid omen level II–V |
| --- | ---: | ---: |
| Easy | 3 | 4 |
| Normal | 5 | 6 |
| Hard | 7 | 8 |

A raid stores its base wave count when created. Level II or higher adds **one** bonus wave, not one extra wave per omen level. The bonus starts with the final base wave's composition. Random additions use the difficulty at the wave's spawn position, so changing difficulty during a raid should not be treated as restarting its wave plan. [Counts and additions][raid-counts] · [Bonus-wave condition][raid-more] · [Spawn context][raid-spawn]

These are the **base** contents before random additions and mounted riders:

| Wave | Pillagers | Vindicators | Evokers | Witches | Ravagers |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 4 | 0 | 0 | 0 | 0 |
| 2 | 3 | 2 | 0 | 0 | 0 |
| 3 | 3 | 0 | 0 | 0 | 1 |
| 4 | 4 | 1 | 0 | 3 | 0 |
| 5 | 4 | 4 | 1 | 0 | 1 |
| 6 | 4 | 2 | 1 | 0 | 0 |
| 7 | 2 | 5 | 2 | 1 | 2 |

[Base composition][raid-composition]

Additional Pillagers/Vindicators vary by difficulty; eligible Normal/Hard waves can add a Witch, and a Normal/Hard bonus wave can add a Ravager. Wave 5 Ravagers receive Pillager riders. From wave 7 onward, the first Ravager receives an Evoker and later Ravagers receive Vindicators. Nearby eligible raiders can also join an active raid. Do not use the table as an exact final headcount. [Random additions][raid-counts] · [Mounted riders][raid-spawn] · [Joining][raider-join]

## Fighting and finding the last raiders

The next wave waits until the tracked raider count reaches zero, then uses another **300-tick** countdown. During combat the bar follows raider health; when only one or two tracked raiders remain, its label shows the remaining count. [Wave timing and label][raid-tick] · [Health bar and wave gate][raid-health]

Prepare solid cover against [Pillager crossbows](../mobs/Pillager.md#behavior), and give [Ravagers](../mobs/Ravager.md#behavior) space: their stun can be followed by a damaging roar. Protect villagers before seeking distant enemies, because both mobs target villagers. These are precautions inferred from the checked targeting and attack code, not a tested fortification layout.

Raid spawns choose surface-height positions around the center, require loaded/ticking terrain and a valid placement, and may use village positions during the last seven seconds of the countdown. Walls alone do not prove that the interior cannot receive a wave. If a bar appears stuck, search nearby terrain, roofs and accessible recesses rather than assuming the next wave is timed independently of survivors. Failed position searches can stop the raid. [Position search][raid-position] · [Spawn failure][raid-win]

Leaving is not a clean reset. A raid retains its tracked mobs, and assigned raiders have special persistence. If the center's chunk is unavailable, the ongoing raid pauses its active work; returning can resume it. The raid also removes invalid, very distant or sufficiently idle outlying members from its tracking, so a disappearing bar is not proof that every hostile nearby died. [Raid persistence][raider-save] · [Active-center check][raid-tick] · [Tracking cleanup][raid-remove]

## Victory, defeat and stopping

- **Victory:** after all required waves and tracked raiders are cleared, the raid waits through its 40-tick completion counter and becomes victorious. Recorded heroes that can be resolved in that server level and are not spectators receive Hero of the Village for **48,000 ticks**, nominally 40 minutes, at the raid's omen level. [Victory rewards][raid-win]
- **Hero eligibility:** the ordinary raider-death callback records the player identified as the damage source's responsible entity. Merely watching the raid or being the player who started it does not itself add that player to this set. Use [Trading](../trading/Trading.md#prices-can-change) for the effect's price implications. [Hero recording][raid-death]
- **Defeat:** if the center is no longer recognized as a village and a nearby qualifying section cannot replace it, an already-spawned raid becomes a loss. Before any wave has spawned, that condition stops it instead. Preserve the settlement's occupied points and villagers. [Village loss and relocation][raid-tick] · [Nearby-center search][raid-more]
- **Stopped without a win:** Peaceful, `disableRaids`, the **48,000-active-tick timeout**, or repeated inability to find a spawn point can stop a raid. The active timeout is not a guaranteed wall-clock limit while the center is unloaded. [Difficulty and timeout][raid-tick] · [Rule handling][raid-clock] · [Spawn failure][raid-win]

Milk removes the player's omen effects; it does not call the active raid's stop routine. For deliberate control, use the raid-specific world rule rather than expecting the separate natural/patrol spawning settings to cancel an event already underway. [Milk removal][clear-effects] · [Raid rule][raid-clock] · [Natural/patrol gate][patrol-tick]

## Related pages

- [Pillager Outpost](../structures/PillagerOutpost.md)
- [Pillager](../mobs/Pillager.md) · [Ravager](../mobs/Ravager.md)
- [Villager jobs](../mobs/Villager.md) · [Trading](../trading/Trading.md)
- [Combat](Combat.md) · [Death and respawn](DeathAndRespawn.md)
- [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. The registered effects run through the living-entity effect tick, and the server ticks its raid manager. This is a source review of the ordinary effect/event path, not a gameplay raid, village-recognition, combat, cancellation or victory test. Modified data packs, dimension types and server settings can change the conditions. [Effect registration][effect-registration] · [Living-entity caller][living-effect-tick] · [Raid-manager caller][raid-server-tick]

[bottle-consume]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L98
[bottle-effect]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L38
[bad-omen]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/effect/BadOmenMobEffect.java#L15-L35
[effect-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L226
[raid-omen]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/effect/RaidOmenMobEffect.java#L15-L32
[milk]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[clear-effects]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L23
[raid-create]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raids.java#L110-L174
[raid-absorb]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L218-L244
[raid-initial]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L124-L131
[raid-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L252-L327
[village-check]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/level/ServerLevel.java#L1441-L1464
[village-poi]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/village/poi/PoiManager.java#L315-L327
[village-tag]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/point_of_interest_type/village.json
[raid-counts]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L716-L777
[raid-more]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L413-L444
[raid-spawn]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[raid-composition]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[raider-join]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L86-L108
[raid-health]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L568-L605
[raid-position]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L637-L665
[raid-win]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L350-L409
[raider-save]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L182-L243
[raid-remove]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L446-L479
[raid-death]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L115-L134
[raid-clock]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raids.java#L82-L104
[patrol-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L370-L408
[effect-registration]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/effect/MobEffects.java#L108-L118
[living-effect-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L820-L844
[raid-server-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/level/ServerLevel.java#L357-L370
