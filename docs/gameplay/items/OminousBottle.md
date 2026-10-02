# Ominous Bottle

An **Ominous Bottle** (`minecraft:ominous_bottle`) applies Bad Omen when drunk. Keep it unconsumed until you intend to prepare a [raid](../mechanics/Raid.md) or an [ominous Trial Spawner encounter](../blocks/TrialSpawner.md#becoming-ominous). Carrying the bottle does not apply the effect. [Registration][bottle-registration] · [Consumption caller][bottle-consume] · [Effect][bottle-effect]

## Obtaining

- **Pillager captains:** the conditional [captain drop](../mobs/Pillager.md#captains-and-ominous-bottles) gives one bottle with amplifier 0–4, displayed as levels I–V, when the captain-without-raid predicate passes. The pool has no player-kill or Looting-count condition; normal mob-loot rules apply. [Drop table][pillager-loot] · [Predicate defaults][captain-predicate]
- **Vault rewards:** both normal and ominous [Vault reward tables](../blocks/Vault.md#normal-versus-ominous-rewards) include possible bottles. Opening and key conditions remain on the Vault guide
- **Inventory item browser:** all five level variants are ordinary food/drink category entries and can be inserted in Survival as well as Creative through MattMC's [browser](../mechanics/InventoryBrowser.md). This is separate from mob or Vault acquisition. [Category call][bottle-list] · [Five variants][bottle-variants] · [List assembly][browser-list] · [Request][browser-client] · [Server handling][browser-server]

## Usage

Finish drinking to apply **120,000 ticks of Bad Omen**, nominally 100 minutes at 20 TPS. The bottle's amplifier sets the effect level; merely possessing a stronger bottle does not upgrade an effect. The consumption path consumes one item under the normal item-consumption rules. [Effect duration/level][bottle-effect] · [Consumption][bottle-consume]

## Behavior

Bad Omen can become **Raid Omen** on entering a recognized village, starting a 600-tick countdown before the saved-position raid-start attempt. It can instead be converted by a qualifying **Trial Spawner** into Trial Omen. Follow the relevant encounter guide for preparation, conversion conditions and rewards; those are distinct systems. [Raid conversion and cancellation](../mechanics/Raid.md#starting-or-avoiding-a-raid) · [Trial conversion](../blocks/TrialSpawner.md#becoming-ominous)

[Milk](MilkBucket.md) removes omen effects along with other effects. Removing Raid Omen before its final tick prevents that effect's pending start call; drinking Milk after a raid begins does not stop the existing raid. [Clear effects][clear-effects] · [Final-tick start][raid-omen]

## Notes

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. This is the bottle's acquisition/use owner; [Raid](../mechanics/Raid.md), [Trial Spawner](../blocks/TrialSpawner.md) and [Vault](../blocks/Vault.md) retain their event/reward ownership. No consumption, drop, browser, raid or trial gameplay test was run.

[bottle-registration]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L2696-L2702
[bottle-consume]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L98
[bottle-effect]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L38
[pillager-loot]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/loot_table/entities/pillager.json
[captain-predicate]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/advancements/critereon/RaiderPredicate.java#L13-L31
[bottle-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1752-L1755
[bottle-variants]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2255-L2261
[browser-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[clear-effects]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/consume_effects/ClearAllStatusEffectsConsumeEffect.java#L20-L23
[raid-omen]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/effect/RaidOmenMobEffect.java#L15-L32
