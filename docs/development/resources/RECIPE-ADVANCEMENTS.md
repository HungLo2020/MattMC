# Recipe-crafted advancement keys

Recipe IDs include subdirectories beneath `data/<namespace>/recipe/`. For
example, `recipe/crafting/crafter.json` loads as `minecraft:crafting/crafter`;
`recipe/smithing/rib_armor_trim_smithing_template_smithing_trim.json` keeps
its `smithing/` prefix. Advancement `conditions.recipe_id` must match this
loaded key exactly. Criterion names are saved progress identifiers and do
not need to match the recipe path; keep them stable when correcting a key.

The bundled Crafter, sherd-only decorated pot and two armor-trim advancements
use 28 such conditions. Their ingredient predicates and OR/AND requirements
are separate from recipe identity. In particular, the pot needs four matching
sherds, the any-pattern advancement needs one pattern, and the exclusive
advancement needs all eight named patterns.

The current Java [`RecipeManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java) and
[`ServerAdvancementManager`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/server/ServerAdvancementManager.java) still decode
these elements through the native resource manager. Ordinary crafting and
smithing pass the loaded recipe holder to `ServerPlayer.triggerRecipeCrafted`.
Crafter dispatch uses the holder's ID when output remains to spawn loose and
checks nearby players. Fully accepted front-container output does not dispatch
that trigger. Recipe-book unlock tracking remains intentionally disabled and
is independent of these advancement listeners.

See [#825](https://github.com/HungLo2020/MattMC/issues/825) for the original
key mismatch and acceptance requirements.

## Verification

Run the resource-loader and advancement-output regression suite with the normal
matching native library and JDK 25:

```sh
./gradlew -PmattmcRustProfile=release -PmattmcTestForks=1 test \
  --tests 'net.minecraft.advancements.RecipeCraftedAdvancementTest'
```

The suite loads bundled recipes and advancements, inventories every
recipe-crafted criterion, and executes Crafter ticks, crafting result slots
and smithing outputs through real trigger listeners and `PlayerAdvancements`.
It checks the different-recipe, out-of-range, front-container and four
brick-instead-of-sherd controls; all 18 trim patterns; eight distinct exclusive
patterns; and higher-priority recipe/advancement replacement followed by
return to the bundled resources.

On 2026-10-14 the unchanged 30-case suite had 23 assertion failures and seven
passing controls before the key correction; all 30 passed afterward, with no
skips. Only the 28 JSON recipe IDs changed between those runs. The resource
loaders reported 1,497 recipes and 1,583 advancements. Existing unrelated
unresolved-tag diagnostics and four malformed Pewen recipes were still logged;
this is not an error-free bundled-pack claim.

World queries, the player shell, network and reward side effects are bounded
test doubles. Recipe matching/assembly, output handling, listeners and progress
awards are real. Reload coverage replaces managers/listeners with fresh unsaved
progress; it does not certify persisted progress through a full live-server
reload. No GUI, live-world or performance benchmark is claimed. The production
change adjusts data identities only, without changing executable hot paths.
