# Powered Rail

The Powered Rail item places `minecraft:powered_rail`, which boosts a cart when powered and brakes it when unpowered.

## Obtaining and use

Craft **six Powered Rails** from six Gold Ingots, one Stick, and one Redstone Dust using the [Transport recipe layout](../mechanics/Transport.md#building-a-basic-rail-route). The placed rail returns its item in ordinary Survival mining without a correct-tool requirement.

Connect it to redstone power and supported track. Power relays only through compatible connected rails of the same type within the search limit; an isolated stationary cart may still need a launch direction. See [Rails](../blocks/Rails.md#powered-rail-and-power-propagation) for connections and [Transport](../mechanics/Transport.md#starting-and-stopping-a-cart) for the existing terminal layout and braking cautions.

## Related pages

- [Rails](../blocks/Rails.md)
- [Rail](Rail.md) and [Minecart](Minecart.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical Rails guide and existing Transport guide provide the recipe, drop, placement, and vehicle-behavior evidence.

- [Rail item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listings](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
