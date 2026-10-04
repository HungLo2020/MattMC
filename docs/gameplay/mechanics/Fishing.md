# Fishing

Fishing uses a [Fishing Rod](../items/FishingRod.md) to catch items from water. It rolls the bundled fishing loot tables when a bite is reeled in; it does not need to hook or consume a visible swimming fish. For treasure, the bobber must also meet the source's open-water test.

## Make a catch

1. Hold a usable rod and use it to cast into water
2. Keep a rod in either hand and remain within 32 blocks of the bobber; otherwise its server-side ownership check removes it
3. Watch the approaching water particles and bite splash, then use the rod again while the bobber is biting
4. Collect the item pulled toward you; each returned loot stack also spawns 1–6 experience near the player

The bite window is **20–40 ticks**, about one to two seconds at 20 TPS. Reeling before a bite normally retrieves no fishing loot. Missing the window restarts the waiting process. Hooking an entity instead uses the entity-pulling branch, not the fishing loot table. [Casting][rod] · [Bobber lifecycle, particles, bite, and retrieval][hook]

## Waiting, weather, and enchantments

The initial waiting counter is randomly selected from **100–600 ticks**, then reduced by the rod's Lure effect. A separate approach counter is selected from **20–80 ticks** before the bite. These counters are not a single guaranteed wall-clock wait.

On each fishing update, rain at the position above the bobber has a 25% chance to add an extra decrement to the waiting/approach counters. When that position cannot see the sky, a separate 50% roll removes one decrement. The bite window itself still decrements once per update. An open, rainy fishing spot can therefore differ from a covered pool even when both hold water. [Counter and weather handling][hook]

- **Lure I–III** supplies 5, 10, or 15 seconds of initial-counter reduction, converted to ticks when the rod casts. It does not extend the bite window or guarantee an immediate catch; non-positive initial rolls are handled by the same waiting-state loop
- **Luck of the Sea I–III** supplies 1, 2, or 3 fishing-luck bonus. This is added to the player's Luck value for loot selection, increasing treasure weight and reducing junk/fish weights in the bundled top-level table

Neither enchantment bypasses treasure's open-water condition. [Lure][lure] · [Luck of the Sea][luck] · [Effect evaluation][enchant-helper] · [Loot weights][fishing-loot]

See [Luck and Unluck](../effects/LuckAndUnluck.md) for the separate status effects, combined fishing weights, and the limits of luck in other loot contexts.

## Open water for treasure

The bobber checks a **5 × 5 horizontal area**, centered on its block, across four layers from **one block below through two blocks above**. Every layer must be uniform under this classification:

- **Water layer:** source water with an empty collision shape
- **Above-water layer:** air or Lily Pads
- **Invalid:** other blocks, flowing/non-source water, collision-bearing waterlogged blocks, or a mixture of the two valid layer types within one layer

The bottom checked layer cannot be an above-water layer, and a water layer cannot occur above an above-water layer. A straightforward arrangement is two complete source-water layers with two clear air layers above, keeping the bobber away from shore, solid obstacles, and flowing edges. Lily Pads count as above-water space in this test, not as water.

The condition is tracked during the approach/bite stages; once spoiled during that attempt, simply clearing an obstacle at the last instant does not necessarily restore eligibility for the same bite. A cramped pool can still provide ordinary catches, but should not be advertised as a treasure pool without checking these conditions. [Exact layer test and tracked state][hook] · [Treasure condition][fishing-loot]

## What you can catch

At **zero combined fishing luck**, with treasure eligible, the top-level weights total 100:

| Category | Base share | Examples |
| --- | --- | --- |
| Fish | 85% | Cod, Salmon, Tropical Fish, Pufferfish |
| Junk | 10% | Lily Pad, Leather, Bone, String, Water Bottle, Bowl, Stick, Tripwire Hook, Rotten Flesh |
| Treasure | 5% | Name Tag, Saddle, enchanted Bow, enchanted Fishing Rod, enchanted Book, Nautilus Shell |

When treasure is ineligible, its entry is removed; the remaining fish and junk weights are renormalized. Thus the base 85/10/5 percentages are **not** unconditional chances in every pool. Luck also changes the weights, so the zero-luck figures should not be reused for an enchanted rod. Effective entry weight is `max(floor(weight + quality × luck), 0)`. [Top-level table][fishing-loot] · [Weight calculation][weights]

Within the fish category, the bundled weights are **Cod 60, Salmon 25, Tropical Fish 2, and Pufferfish 13**. These are shares of fish-category results, not of all casts. Junk also includes a damaged rod, damaged leather boots, and a ten-Ink-Sac stack; Bamboo is an additional junk entry only in Jungle, Sparse Jungle, and Bamboo Jungle. Treasure's six entries have equal base weights within that category; the three enchanted entries use level-30 loot enchanting rather than a fixed enchantment guarantee. [Fish table][fish] · [Junk table][junk] · [Treasure table][treasure]

## Durability and repairs

A normal Fishing Rod has **64 durability**. Casting itself does not apply wear; retrieving the bobber returns the wear amount:

| Retrieval result | Normal durability cost |
| --- | --- |
| Fishing loot during a bite | 1 |
| Hooked dropped-item entity | 3 |
| Other hooked entity | 5 |
| Bobber on the ground | 2, overriding the preceding result |
| Empty retrieval without the above conditions | 0 |

Creative and durability effects can alter ordinary wear. MattMC keeps a fully damaged rod as a broken stack, but its normal item-use entry point refuses the action. Repair it rather than assuming that retained means usable. [Rod wear dispatch][rod] · [Retrieval costs][hook] · [Broken-item guard][stack] · [Durability and repair](Durability.md)

Two matching rods can be repaired by crafting, a Grindstone, or an Anvil under those systems' rules. Choose carefully for named or enchanted rods: crafting repair does not preserve ordinary enchantments, while a Grindstone removes non-curse enchantments. [Repair comparison](Durability.md#choose-a-repair-method) · [Anvil operations](AnvilMechanics.md)

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of casts, waits, open-water eligibility, loot distribution, entity pulling, or repairs was run. Timings assume 20 TPS, and data packs/components can change the reviewed defaults.

Related: [Fishing Rod](../items/FishingRod.md) · [Enchanting](../enchanting/Enchanting.md) · [Mechanics](Mechanics.md) · [Local rain and weather](TimeWeatherAndSleep.md#rain-thunder-and-the-place-you-stand)

[rod]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/FishingRodItem.java
[hook]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java
[lure]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/lure.json
[luck]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/luck_of_the_sea.json
[enchant-helper]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L401-L410
[fishing-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing.json
[fish]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing/fish.json
[junk]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing/junk.json
[treasure]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/gameplay/fishing/treasure.json
[weights]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L129-L131
[stack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemStack.java#L382-L390
