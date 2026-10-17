# Pewen Chest Boat

**Pewen Chest Boat** (`minecraft:pewen_chest_boat`) carries **one rider and 27 storage slots**. The item stacks to **one**. [Item registration][item] · [Capacity][chest]

## Obtaining

The bundled definition intends **1 [Pewen Boat](PewenBoat.md) + 1 [Chest](../blocks/Chest.md) → 1 Pewen Chest Boat**, in any arrangement. **Do not treat it as a working Survival crafting recipe.** Its two ingredients use legacy item objects, which the active shapeless ingredient decoder does not accept; the loader logs the parse failure and omits the recipe. This is a source-derived incompatibility, not a reproduced gameplay test. [Recipe definition][recipe] · [Shapeless decoder][shapeless] · [Ingredient codec][ingredient] · [Accepted holder forms][holder] · [Load failure handling][loader]

The [Pewen family cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution) also cover the broken log-to-plank route needed by the ordinary boat. No alternative recipe for this chest boat was found in the bundled data or optional data packs.

This item has a [Creative inventory entry][creative].

### Catalog name

For default item stacks with the reviewed bundled English resources, search the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) for `pewen_chest_boat`, including the underscores. The source-resolved display name is `item.minecraft.pewen_chest_boat`; this page's readable title is not a bundled translation. Custom item names, another language or resource-pack translations can change that text. This name/search guidance is source-derived, without a running-client check. [Default item names][catalog-name-init] · [Hover name][catalog-hover-name] · [Missing-key fallback][catalog-name-fallback] · [Name search][catalog-name-search] · [Bundled English][catalog-english]

## Usage

Place the vehicle in clear space, board without Sneak/Crouch, and row with your movement controls. Use **Sneak/Crouch + interact** to open storage from outside, or your **inventory control while riding**. Follow the shared [chest-boat storage guide](OakBoat.md#chest-boat-storage) for access and persistence, and [rowing guide](OakBoat.md#placing-and-rowing) for movement. [Placement][placement]

The item and its matching chest-boat entity are both registered. Placement uses the shared boat-item callback, and the registered entity supplies this same Pewen Chest Boat item for recovery. [Entity registration][entity]

## Behavior

Damaging the placed boat until it breaks returns a fresh matching item when **`doEntityDrops` is enabled**. Creative attacks discard the vehicle instead. **Cargo drops separately; it is not packed inside the recovered item.** Unload valuable supplies before breaking the vehicle. [Damage and item drop][damage] · [Cargo removal][cargo]

**Neither Pewen boat form is furnace fuel with the bundled tags.** Both are absent from the boat and chest-boat fuel groups, and the default fuel table has no separate Pewen-boat entry. [Fuel table][fuel] · [Boat tag][boats-tag] · [Chest-boat tag][chest-tag]

A [Dispenser](Dispenser.md) ejects this item rather than placing its vehicle: Pewen has no boat-placement dispenser registration and uses the default item behavior. Place it by hand. [Boat dispenser registrations][dispenser] · [Fallback selection][default-dispenser] · [Item ejection][ejected-item]

## Notes

Pewen uses the same one-seat, 27-slot chest-boat implementation as the ordinary wood chest boats. Its recipe, fuel, and dispenser exceptions above still apply. [Chest-boat capacity][chest]

Related: [Pewen Boat](PewenBoat.md) · [Oak Boat](OakBoat.md) · [Oak Boat with Chest](OakBoatWithChest.md) · [Transport](../mechanics/Transport.md#choosing-a-vehicle) · [Items](Items.md)

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. Registrations, recipes and their loader, fuel tags, and shared vehicle callbacks were checked. No in-game crafting, placement, rowing, cargo, recovery, dispenser, or appearance test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1270-L1272
[entity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java#L1003-L1010
[creative]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1534
[recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/pewen_chest_boat.json
[chest]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L30-L49
[shapeless]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L83-L91
[ingredient]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L32-L33
[holder]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/resources/HolderSetCodec.java#L25-L57
[loader]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener.java#L59-L91
[placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/BoatItem.java#L30-L87
[damage]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/VehicleEntity.java#L34-L75
[cargo]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/vehicle/AbstractChestBoat.java#L63-L76
[fuel]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L108
[boats-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/boats.json
[chest-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/item/chest_boats.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L145-L164
[default-dispenser]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L102-L112
[ejected-item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L46

[catalog-name-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L122-L125
[catalog-hover-name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L792-L817
[catalog-name-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/locale/Language.java#L110-L114
[catalog-name-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L115-L132
[catalog-english]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json
