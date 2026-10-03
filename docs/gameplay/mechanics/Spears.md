# Spears: release thrusts and moving contact

**Leave room in front of you and aim along the target's path.** MattMC's seven material Spears have an ordinary melee attack, a held-use contact attack based on closing speed, and a release thrust. Holding a Spear does not turn every attack into the same charged strike. [Registered Spears][registrations] · [Held contact][contact] · [Release thrust][release]

## Choose the attack for the situation

- **Ordinary attack:** use the normal attack control with a Spear in your main hand. It follows [Combat's attack-charge and critical rules](Combat.md#time-your-melee-attacks), using ordinary interaction range. Every material has **1.2 attack speed** before other modifiers, a nominal full-charge interval of about **16.7 ticks**. Spears are not in the swords tag, so this does not grant a [Sword sweep](Swords.md). [Main-hand modifiers][attributes] · [Registration settings][components] · [Player defaults][player-attributes] · [Base speed][speed] · [Charge][charge] · [Normal attack][normal-attack] · [Sword membership][sword-membership]
- **Release thrust:** hold use for at least **8 ticks**, then release while aiming. At 20 TPS the minimum is **0.4 seconds**. Releasing earlier produces no release thrust. Holding longer does not increase its material-based damage; the weapon remains in your hand. A qualifying release also resets the ordinary melee charge timer, even if no target is hit. [Threshold][constants] · [Use start][start-use] · [Release][release] · [After-release actions][lunge]
- **Moving contact:** during the beginning of held use, a target in the thrust line can be struck before release if you are closing on it fast enough. This is a separate speed-based check, explained below. [Contact checks][contact]

The standard client consumes ordinary attack clicks while an item is being used. Release use before trying an ordinary melee attack; clicking attack while holding use does not invoke an extra Spear attack mode. [Input handling][controls]

## Choose a material

The damage column gives both the default full-strength ordinary hit and the release thrust's starting damage, in **health points**; two points equal one heart. It excludes enchantments, ordinary critical bonuses, defenses, and other modifiers. Held-contact damage uses a different calculation. [Registrations][registrations] · [Material values][materials] · [Attack modifiers][attributes] · [Player base damage][player-attributes] · [Release damage][release]

| Spear | Starting damage | Maximum durability | Crafting tip / Anvil repair material |
| --- | ---: | ---: | --- |
| [Wooden](../items/WoodenSpear.md) | 5 | 59 | Planks in the wooden-tool-material tag |
| [Stone](../items/StoneSpear.md) | 6 | 131 | Cobblestone, Blackstone, or Cobbled Deepslate |
| [Copper](../items/CopperSpear.md) | 6 | 190 | Copper Ingot |
| [Iron](../items/IronSpear.md) | 7 | 250 | Iron Ingot |
| [Golden](../items/GoldenSpear.md) | 5 | 32 | Gold Ingot |
| [Diamond](../items/DiamondSpear.md) | 8 | 1,561 | Diamond |
| [Netherite](../items/NetheriteSpear.md) | 9 | 2,031 | Netherite Ingot; obtained by smithing |

Copper lasts longer than Stone at the same starting damage; Golden has the lowest durability and the same starting damage as Wooden. None gains a faster ordinary attack or a shorter release threshold from its material. Netherite adds fire-damage resistance to the item, not to its holder. [Material settings][materials] · [Shared attributes][components] · [Shared threshold][constants] · [Fire-resistant component][fire-resistant] · [Item damage check][item-damage-resistance]

## Craft or request a Spear

For Wooden through Diamond, use a **3 × 3 crafting grid**: put the material in the **top-right**, one Stick in the **center**, and another Stick in the **bottom-left**. Each bundled recipe produces one Spear. The six recipes use the same material tags as their Anvil repairs. [Wooden recipe][wooden-recipe] · [Stone recipe][stone-recipe] · [Copper recipe][copper-recipe] · [Iron recipe][iron-recipe] · [Golden recipe][golden-recipe] · [Diamond recipe][diamond-recipe] · [Repair registration][components]

Accepted materials are [planks][wooden-repair], [the three stone materials][stone-repair], [Copper Ingot][copper-repair], [Iron Ingot][iron-repair], [Gold Ingot][gold-repair], and [Diamond][diamond-repair]. Netherite uses a **Diamond Spear + Netherite Ingot + Netherite Upgrade Smithing Template** at a Smithing Table. Its transformation copies the base stack's changed components, so an upgrade should not be treated as a blanket reset of damage or custom data. Inspect the output. [Smithing recipe][smithing] · [Netherite material][netherite-repair] · [Transformation][smithing-copy] · [Result copy][transmute] · [Component patch copy][component-copy]

All seven Spears are ordinary combat-tab entries. MattMC's [inventory item browser](InventoryBrowser.md) can request ordinary listed items in Creative. This route is separate from collecting crafting ingredients or finding loot. [Tab entries][tab]

## Aim beyond close contact

Both held contact and release thrust check a line from your eyes in your look direction. Their nominal player reach is **2 to 4.5 blocks**, or up to **6.5** with the instant-build ability. A **0.125-block tolerance** and expanded target hitboxes affect the exact boundary; these distances are measured to the line's hitbox intersection, not the target's feet or center. If an enemy is too close for a thrust, make space or use an ordinary attack. [Target line][targets] · [Reach and tolerance][reach] · [Constants][constants]

Collidable blocks stop the line; fluids are ignored by that block clip. The line can select multiple targets, ordered nearest first. It skips your fellow vehicle passengers, allied living targets, your own tamed animals, and players you cannot harm, along with other eligibility checks. Unrelated animals can still be selected. **These are the use attack's filters; ordinary attack clicks follow their own rules.** [Target selection][targets] · [Ordinary attack][normal-attack]

Each selected target still has to accept the attack. Enchantments modify the submitted damage, and the target's damage handling can reduce or reject it. Recent damage can prevent another full hit, so contact followed immediately by release is not a promise of adding both displayed damage values together. [Attack application][damage] · [Living-target blocking and damage cooldown][living-damage]

## Moving contact and riding

Held contact is checked only while elapsed use is **0 through 40 ticks**, approximately the first two seconds at 20 TPS. There is no eight-tick wait for this mode. After that window, continuing to hold use does not keep checking contact hits, but a later release can still thrust. [Contact window][contact] · [Release condition][release]

For a player, the required **closing speed along the look direction is at least 1 block per second**. The code subtracts the target's projected movement from yours and ignores a negative result. Moving toward a target helps; a target approaching you can also qualify while you stand still. Sideways speed alone is not the quantity checked. [Speed comparison][contact] · [Movement conversion and threshold][movement-cooldown]

Before enchantments and defenses, contact damage is the attacker's **base attack-damage attribute plus the closing speed rounded down**. For a default player this is `1 + floor(closing speed)`: a checked closing speed of 4.8 gives a starting value of 5 points. The Spear material's main-hand damage bonus does not enter that calculation. [Contact damage][contact] · [Player base][player-attributes] · [Material modifier][attributes]

A contact attempt is remembered before its speed or damage succeeds. That attacker/target pair is skipped until **more than 10 ticks** have passed; even pointing at a target too slowly can postpone a subsequent contact attempt. Release thrusts do not consult this contact timer. [Attempt order][contact] · [Contact cooldown][movement-cooldown] · [Release loop][release]

Riding is allowed for these attacks. Contact uses the server's known movement, with vehicle movement handling for passengers; merely mounting does not supply a fixed damage bonus. An accepted contact attack also attempts to knock the target back and **dismount it**. The release thrust applies knockback but does not request a dismount. [Movement helper][movement-cooldown] · [Server player movement][mounted-movement] · [Vehicle movement][entity-movement] · [Contact flags][contact] · [Release flags][release] · [Hit effects][damage]

## Lunge and other enchantments

**Lunge I–III** adds a horizontal push after a qualifying release, including a release that hits nothing. You must not be riding, gliding with Elytra, or in Water or rain. A player without instant-build ability also needs at least **7 food points**. Looking vertically does not provide an upward launch. The push scales with level; its wear request is **1 durability**, and player exhaustion is **4 per level**. Exhaustion is not a direct deduction of that many food points; see [Hunger](Hunger.md). [Lunge definition][lunge-data] · [Release and Lunge checks][lunge]

The thrust's target checks happen before the Lunge push. Do not count the resulting travel as extra reach for the thrust you just released. [Release order][release] · [Lunge push][lunge]

With the bundled tags:

- **Lunge** and **Unbreaking** are eligible for normal Spear enchanting-table selection, subject to the usual level and cost rules. [Lunge][lunge-data] · [Lunge items][lunge-tag] · [Unbreaking][unbreaking-data] · [Durability items][durability-tag] · [Table pool][table-tag] · [Non-treasure pool][non-treasure]
- **Smite** and **Bane of Arthropods** support Spears through the weapon tag, but their primary item tag is sword-only. Use an applicable enchanted book at an Anvil; they are not normal Spear table rolls, and their damage-enchantment exclusivity prevents combining them. [Weapon items][weapon-tag] · [Smite][smite] · [Bane][bane] · [Primary-item rule][enchant-eligibility] · [Table selection][table-selection] · [Anvil acceptance][anvil-enchantments] · [Exclusivity][damage-exclusive]
- **Mending** and **Curse of Vanishing** support Spears through durability-related tags. [Mending][mending-data] · [Vanishing][vanishing-data] · [Vanishing items][vanishing-tag]
- Sword-only or sharp-weapon-only support does not include Spears. Do not plan around ordinary application of Sharpness, Knockback, Looting, Sweeping Edge, or Fire Aspect; a Spear is also not a Trident for its exclusive enchantments. [Sharp-weapon items][sharp-tag] · [Sword items][sword-tag] · [Fire Aspect items][fire-tag] · [Trident items][trident-tag]

The excluded enchantments' definitions select those narrower tags: [Sharpness][sharpness-data], [Knockback][knockback-data], [Looting][looting-data], [Sweeping Edge][sweeping-data], and [Fire Aspect][fire-aspect-data]. Commands or changed data can create different combinations; the list describes ordinary bundled eligibility.

## Wear, repair, and broken Spears

A successful attack on a living target normally requests **1 durability per target**. A thrust through several targets can therefore cost several durability points; a miss has no hit wear, though an eligible Lunge can still add its separate wear. Enchantment durability processing and infinite-material abilities can change actual wear. [Registered weapon][components] · [Spear hit hooks][damage] · [Shared hit wear][wear] · [Lunge wear][lunge] · [Durability processing][broken]

Save the Spear for fighting: its registered mining component has **no special mining rules**, uses basic speed **1.0**, and requests **2 durability** for a broken block with nonzero hardness. It does not provide the Sword's Cobweb mining bonus or correct-tool drops for tool-gated blocks. [Registered tool][components] · [Spear tool settings][attributes] · [Mining rules][tool] · [Block wear][mining]

At maximum damage, the normal durability path **retains the broken Spear**. An already-broken stack cannot start use; the contact-tick and release entry points also check for breakage. Its ordinary equipment attributes are removed. Repair it before relying on its weapon functions again. These guards do not establish that a multi-target attack is interrupted between every target when the last durability point is spent. [Retained stack][broken] · [Use guard][start-use] · [Contact guard][contact] · [Release guard][release] · [Equipped attributes][equipment] · [Break callback][break-effects]

An Anvil accepts the material in the table above. Matching-item repairs and their enchantment/data tradeoffs belong to [Durability and repair](Durability.md#choose-a-repair-method); check that guide before combining valuable Spears. [Repair components][components] · [Material repair calculation][repair]

## Related pages

- [Combat](Combat.md): ordinary attack timing, defenses, criticals and projectile rules
- [Swords](Swords.md) and [Axes and Hoes](AxesAndHoes.md): their separate weapon and tool rules
- [Trident](../items/Trident.md): throwing, return and Riptide behavior
- [Enchanting](../enchanting/Enchanting.md), [Anvil operations](AnvilMechanics.md), and [Durability](Durability.md)
- [Items](../items/Items.md) and [Mechanics](Mechanics.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `bb9a8a060da02b64f23508b77794fb0f79307de4`. Registration, active input/use dispatch, attack loops, components, bundled recipes and enchantment tags were inspected. No in-game timing, mounted-combat, damage, enchantment, recipe, or wear-out test was run. Seconds assume 20 TPS. Modified item components, attributes, data packs, and target-specific behavior can change results.

[anvil-enchantments]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L179-L205
[attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L68-L81
[bane]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/bane_of_arthropods.json#L60-L79
[break-effects]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3540-L3552
[broken]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L429-L485
[charge]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L1723-L1735
[component-copy]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L627-L636
[components]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2800-L2812
[constants]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L40-L52
[contact]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L104-L137
[controls]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/client/Minecraft.java#L2102-L2122
[copper-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/copper_spear.json#L1-L16
[copper-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/copper_tool_materials.json#L1-L5
[damage]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L212-L240
[damage-exclusive]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/enchantment/exclusive_set/damage.json#L1-L10
[diamond-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/diamond_spear.json#L1-L16
[diamond-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/diamond_tool_materials.json#L1-L5
[durability-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/durability.json#L1-L26
[enchant-eligibility]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/enchantment/Enchantment.java#L124-L130
[entity-movement]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Entity.java#L3913-L3915
[equipment]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2668-L2683
[fire-aspect-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/fire_aspect.json#L24-L43
[fire-resistant]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/fire_aspect.json#L1-L6
[gold-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/gold_tool_materials.json#L1-L5
[golden-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/golden_spear.json#L1-L16
[iron-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/iron_spear.json#L1-L16
[iron-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/iron_tool_materials.json#L1-L5
[item-damage-resistance]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[knockback-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/knockback.json#L19-L34
[living-damage]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1164-L1195
[looting-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/looting.json#L27-L42
[lunge]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L260-L289
[lunge-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/lunge.json#L1-L20
[lunge-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/lunge.json#L1-L5
[materials]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L32
[mending-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/mending.json#L15-L30
[mining]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Item.java#L236-L252
[mounted-movement]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/server/level/ServerPlayer.java#L2130-L2138
[movement-cooldown]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L292-L318
[netherite-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/netherite_tool_materials.json#L1-L5
[non-treasure]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/enchantment/non_treasure.json#L1-L40
[normal-attack]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L971-L1023
[player-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L220-L227
[reach]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L316-L335
[registrations]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L2046-L2052
[release]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L139-L163
[repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/inventory/AnvilMenu.java#L135-L148
[sharp-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/sharp_weapon.json#L1-L6
[sharpness-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/sharpness.json#L17-L36
[smite]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/smite.json#L24-L43
[smithing]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/netherite_spear_smithing.json#L1-L9
[smithing-copy]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/SmithingTransformRecipe.java#L32-L34
[speed]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L15-L17
[start-use]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L88-L102
[stone-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/stone_spear.json#L1-L16
[stone-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/stone_tool_materials.json#L1-L7
[sweeping-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/sweeping_edge.json#L27-L42
[sword-membership]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/swords.json#L1-L11
[sword-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/sword.json#L1-L5
[tab]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1599-L1605
[table-selection]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/enchantment/EnchantmentHelper.java#L587-L600
[table-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/enchantment/in_enchanting_table.json#L1-L5
[targets]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/SpearItem.java#L165-L205
[tool]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/component/Tool.java#L39-L57
[transmute]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/crafting/TransmuteResult.java#L47-L50
[trident-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/trident.json#L1-L5
[unbreaking-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/unbreaking.json#L59-L75
[vanishing-data]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/enchantment/vanishing_curse.json#L8-L23
[vanishing-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/vanishing.json#L1-L8
[weapon-tag]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/enchantable/weapon.json#L1-L7
[wear]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/ItemStack.java#L546-L573
[wooden-recipe]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/recipe/wooden_spear.json#L1-L16
[wooden-repair]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/wooden_tool_materials.json#L1-L5
