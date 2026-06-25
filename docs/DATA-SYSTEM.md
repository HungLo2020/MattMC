# Data System Overview

MattMC uses Minecraft's data-driven resource system for content such as recipes,
loot tables, tags, advancements, models, blockstates, and generated reports.

Most generated data is produced through Gradle data-generation tasks and written
to the ignored `generated/` directory. Curated data that should ship with the
game belongs under `src/main/resources/`.

## Common Locations

| Path | Purpose |
| --- | --- |
| `src/main/resources/data/` | Runtime datapack data such as recipes, loot tables, and tags |
| `src/main/resources/assets/` | Runtime client assets such as models, blockstates, atlases, and textures |
| `generated/data/` | Generated server-side reference output |
| `generated/assets/` | Generated client-side reference output |
| `generated/reports/` | Generated diagnostic/reference reports |

## Related Documentation

- [Data Generation Guide](DATA-GENERATION.md)
