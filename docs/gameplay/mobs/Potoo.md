# Potoo

A **Potoo** is a small flying animal that can be attracted with **Beetroot Seeds**. Several apparent care features are incomplete in the checked MattMC bundle: the seed lure works independently of its missing breeding-food tag, and the perch behavior has no bundled eligible blocks. Start with the current limits before building a breeding pen or expecting a hunting companion. [Active goals][potoo-goals] · [Food check][potoo-food] · [Perch check][potoo-perch-check]

## Obtaining

The [Potoo Spawn Egg](../items/PotooSpawnEgg.md) is the verified ordinary creation route. It appears in the ordinary item list and can be requested through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. Egg placement creates the registered `minecraft:potoo` directly. [Egg registration][potoo-egg] · [Listing][potoo-list] · [Egg placement][egg-place] · [Entity registration][potoo-id]

No Potoo entry was found in the checked active biome spawn lists, and no separate natural creation caller was found. A bright-spawn helper and a Leaves-or-Logs obstruction check exist, but they do not put Potoos into a biome's population. Do not assume searching a jungle at night is a verified acquisition route. [Ordinary spawn-list dispatch][natural-list] · [Chunk-generation list][chunk-list] · [Species checks][potoo-spawn]

A matching Potoo Spawn Egg used on a Potoo can create a baby through its offspring factory, even though ordinary food breeding is not available from the bundled tags. The baby needs **24,000 ticking game ticks**, about **20 minutes**, to grow and cannot enter its flying state while young. [Egg dispatch][egg-dispatch] · [Egg offspring path][egg-baby] · [Potoo offspring factory][potoo-baby] · [Growth][age] · [Baby flight restriction][potoo-no-baby-flight]

## Luring and care

Hold **Beetroot Seeds in either hand** to attract a nearby Potoo. Its lure goal checks within its configured **10-block** temptation range and tries to approach the holder; this is a temporary food lure, not taming or an owner-following command. Use [Root crops](../blocks/RootCrops.md) for growing Beetroots and obtaining more seeds. [Lure and range][potoo-goals] · [Either-hand check][tempt] · [Following movement][tempt-move]

**Beetroot Seeds are not a verified breeding or healing food for this Potoo.** Its interaction delegates to ordinary animal feeding, which consults the separate `minecraft:potoo_breedables` item tag. The checked active item-tag resources contain no definition for that tag. Consequently the ordinary bundle supplies no matching food to start love mode or accelerate a baby's growth, and the direct interaction adds no healing routine. [Interaction][potoo-interact] · [Food predicate][potoo-food] · [Tag declaration][potoo-tags] · [Namespace][tag-namespace] · [Item membership check][item-tag-check] · [Bound-tag membership][tag-membership] · [Shared feeding][animal-feed]

There is no ordinary taming, sit-toggle, or persistent player-owner interaction in the active class. A [Lead](../items/Lead.md) uses the shared mob leash rules, and a roofed enclosure is useful for keeping an adult flyer nearby. Feeding seeds does not replace confinement. [Interaction][potoo-interact] · [Active goals][potoo-goals] · [Leash eligibility][lead-rule] · [Lead interaction][lead-use]

## Behavior

### Flight, perches, and sleep

Potoos have **8 health points, or 4 hearts**. Adults can choose flight destinations and later seek the ground, while the active goals include floating and panic behavior. Ordinary movement's landing-fall callback is overridden, and the active damage check also ignores in-wall suffocation damage. These specific protections do not make the bird invulnerable. [Registered attributes][potoo-attribute] · [Health and goals][potoo-goals] · [Flight updates][potoo-flight] · [Idle flight][potoo-idle] · [Movement fall caller][fall-caller] · [Fall override][potoo-fall] · [In-wall immunity][potoo-riding]

The special perch system requires blocks in **`minecraft:potoo_perches`**. That block tag has no bundled definition in the checked active resources, so there is currently no verified ordinary perch block to provide. A Potoo standing on a Log is not evidence that its special perching state is active. [Perch tag][potoo-tags] · [Namespace][tag-namespace] · [Perch eligibility][potoo-perch-check] · [Block membership][block-tag-check] · [Bound-tag membership][tag-membership]

If a data pack supplies perch members, the existing code also checks clearance above and on the chosen side. Its goal can seek a valid perch, stop moving there, and retain it longer during daytime. The special sleep state depends on already being perched, daytime, and having no live attack target; it is not a promise that the default bird sleeps on any nearby fence or tree. [Perch start][potoo-perch-start] · [Approach and continuation][potoo-perch-end] · [Sleep condition][potoo-sleep]

### Calls and combat limits

Its call behavior samples recent **local brightness**. An awake bird without a live target can call frequently in complete darkness and less often at low light. This is not a detector for a chest, enemy, or particular biome. [Brightness sampling][potoo-light] · [Buffered light value][potoo-eye] · [Call conditions][potoo-call]

The class contains an attack routine, but its registered goals supply no ordinary prey-selection or retaliation target, and there is no player interaction that commands an attack. Its falconry launch callback is empty; the player-riding update also detaches it from the player. Treat it as an untrained flying animal, not a verified shoulder pet or hunting tool. [Registered goals][potoo-goals] · [Conditional attack routine][potoo-combat] · [Player interaction][potoo-interact] · [Empty launch callback][potoo-food] · [Player-riding behavior][potoo-riding]

## Persistence and drops

Potoos inherit the animal rule that disables ordinary distance-based despawning. Their age and flying/perching fields are saved; saving a perch coordinate does not make an untagged block valid on reload. There is no taming requirement to obtain that animal retention rule, but an adult can still leave an open enclosure. [Animal retention][no-distance] · [Saved age][age-save] · [Saved perch and flight state][potoo-save] · [Live perch validation][potoo-sleep]

No active `minecraft:entities/potoo` loot-table resource is bundled. The entity's normal loot key therefore reaches the loaded-table lookup's empty fallback, so **no ordinary item death drops are verified**. Generic equipment-drop rules are separate and do not establish a natural Feather drop. [Default entity loot key][loot-key] · [Entity lookup][entity-loot] · [Mob lookup][mob-loot] · [Death-table dispatch][loot-load] · [Empty fallback][empty-loot] · [Equipment rules][equipment-loot]

## Notes

- Entity ID: `minecraft:potoo`
- Spawn Egg ID: `minecraft:potoo_spawn_egg`
- Registered as a creature with a **0.4 × 0.7-block** adult size. [Entity registration][potoo-id] · [Egg registration][potoo-egg]

## Related pages

- [Potoo Spawn Egg](../items/PotooSpawnEgg.md)
- [Root crops](../blocks/RootCrops.md): growing Beetroots and harvesting seeds
- [Beetroot Seeds](../items/BeetrootSeeds.md)
- [Lead](../items/Lead.md)
- [Seagull](Seagull.md)
- [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Checked registered creation and attributes, active biome and item/block-tag directories, inherited interaction, lure, offspring, movement/perch and target callbacks, saved data, and the loaded loot fallback. No in-game spawn, lure, breeding, perching, flight, combat, or drop test was run. Missing food/perch/loot resources refer to this checked bundle; added data packs or code can change those limits.

[potoo-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L76-L87
[potoo-food]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L476-L483
[potoo-perch-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L355-L359
[potoo-egg]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1933
[potoo-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2064
[egg-place]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L103
[potoo-id]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1069-L1071
[natural-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L325
[chunk-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L369
[potoo-spawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L127-L142
[egg-dispatch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1079-L1102
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181
[potoo-baby]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L361-L365
[age]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L167
[potoo-no-baby-flight]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L295-L300
[tempt]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java#L53-L65
[tempt-move]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/TemptGoal.java#L105-L130
[potoo-interact]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L426-L430
[potoo-tags]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L69-L71
[tag-namespace]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/misc/AMTagRegistry.java#L250-L255
[item-tag-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/ItemStack.java#L330-L332
[tag-membership]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/core/Holder.java#L175-L178
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[lead-rule]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1219
[lead-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2162-L2174
[potoo-attribute]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L215
[potoo-flight]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L179-L200
[potoo-idle]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L495-L545
[fall-caller]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L738-L746
[potoo-fall]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L461-L466
[potoo-riding]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L256-L284
[block-tag-check]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L848-L850
[potoo-perch-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L565-L582
[potoo-perch-end]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L622-L687
[potoo-sleep]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L201-L214
[potoo-light]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L167-L180
[potoo-eye]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L367-L373
[potoo-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L216-L241
[potoo-combat]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L693-L729
[no-distance]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[age-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[potoo-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntityPotoo.java#L326-L353
[loot-key]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L3922-L3924
[mob-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L402-L405
[loot-load]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[empty-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[equipment-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
