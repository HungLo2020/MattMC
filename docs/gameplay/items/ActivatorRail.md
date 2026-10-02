# Activator Rail

The Activator Rail item places `minecraft:activator_rail`, a track component that invokes a passing cart's activation behavior.

## Obtaining and use

Craft **six Activator Rails** from six Iron Ingots, two Sticks, and one Redstone Torch using the [shared rail recipe](../blocks/Rails.md#crafting-and-collecting). Ordinary Survival mining returns the rail without a correct-tool requirement.

Powered Activator Rails eject riders from plain Minecarts, disable a Hopper Minecart's own collection, and can prime TNT Minecarts. An unpowered Activator Rail re-enables Hopper cart collection. These effects are not the Powered Rail's boost/brake behavior. See the [Rails block guide](../blocks/Rails.md#activator-rail-effects) for exact cart distinctions, power propagation, and movement-model limits.

## Related pages

- [Rails](../blocks/Rails.md)
- [Powered Rail](PoweredRail.md) and [Minecart](Minecart.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical Rails guide and existing Transport guide provide the recipe, drop, placement, and vehicle-behavior evidence.

- [Rail item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listings](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
