# Shared biome weather policy

`Biome.getPrecipitationAt`, `shouldFreeze` and `shouldSnow` use a Rust weather
frame. Java binds actual world/position observations and projects the resulting
boolean or precipitation enum. The replaced Java decision algorithms are removed.
This is a connected increment in the unfinished world-generation migration.

## Working on the boundary

The [native policy](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/weather)
embeds the existing [temperature invocation](RUST-TEMPERATURE.md) on a cache
miss. A cache hit uses the same actual per-biome/per-calling-thread FIFO.
There is no weather-specific cache or replayed temperature calculation.
The completed temperature child retires before later world effects; a later
world failure keeps the already committed temperature history.

The [Java adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/biome/NativeBiomeWeather.java)
performs genuine VM operations only when requested. Preserve their order:

- Snow queries read sea level before testing precipitation. A dry biome then
  skips the position, cache, height, light and materials.
- Temperature reads the original packed key before obtaining its calling-thread
  cache. Its callbacks can reenter and populate that same cache.
- Cold freezing/snow queries make a fresh world-height observation after
  temperature, even when temperature was cached. Height gates light; light
  gates material observations.
- Freezing reads block state, then fluid state, then fluid type, then block kind.
  Edge checks derive actual west/east/north/south positions through the original
  virtual methods and stop at the first non-water answer. Retained aliases and
  throwing getters must remain observable.
- Snow tests air before snow membership and calls the actual default snow
  state's survival method only after the material test passes.

Do not replace those operations with an eager coordinate/world snapshot. No
native cache lock or borrowed synthesis pointer survives a VM callback. The
parent owns its native frame until the Java `finally` release; an active
embedded child owns its actual cache Arc even if its Java facade is collected.
All borrowed-pointer construction stays in the private FFI boundary. Block
survival and world/light implementation remain their own systems; this adapter
does not change lighting propagation or save custody.

## Focused verification

Use JDK25 and a coherent release Java/native build:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*BiomeWeatherContractTest' \
  --tests '*NativeBiomeWeatherTest' \
  --tests '*BiomeTemperatureContractTest' -x testRustNative
python3 DevUtils/tests/worldgen/biome_weather/VerifyWeather.py \
  --pre-runtime build/worldgen/weather/pre-77-runtime \
  --output build/worldgen/weather/portable-review
python3 DevUtils/tests/worldgen/biome_weather/VerifyWeatherServer.py \
  --pre-runtime build/worldgen/weather/pre-77-runtime \
  --output build/worldgen/weather/server-review
```

The portable comparison requires complete immutable Frozen/pre-change runtimes
and the recorded classpaths described in the
[chunk-generation guide](../chunk-loading/RUST-CHUNK-GENERATION.md).
It runs the same public weather and temperature contracts against all three
runtimes and checks raw temperature/FIFO history independently. Native-owner
checks exercise actual parent/child retirement and collection inside a genuine
coordinate callback. Intentional omitted-owner/retirement and reordered-effect
controls must fail at their intended assertions.

The short server screen uses actual registered biomes and `ServerLevel`
block/light services. It measures cached precipitation, cold water freezing,
snow on stone, and frozen-biome misses, and verifies the unchanged scene and
actual public/native work. Compilation/GC-quiet samples, allocation and loaded
source/class/native origins are recorded. It is intermediate evidence, not a
completed-world-generation performance or gameplay/FPS pass. Final subsystem
acceptance remains governed by the
[migration policy](../../tooling/MIGRATION-ACCEPTANCE.md).

## Intermediate screen — October16,2026

The combined candidate uses master `993eafb5…` plus this weather increment,
release native `1c230f26…`, JDK25 and Rust/Cargo1.94.0 on HungLoDE.
It passed 80 focused Java checks, including incoming inventory/broadcast
integration; 23 portable weather/temperature contracts passed on each of
original Frozen, sealed published pre-change `77b6d654…`/`7ac75c26…`, and
Current. All three matched the 710,520-observation raw temperature/FIFO corpus.
Three native-owner Java checks and eight standalone actual Rust checks passed.
Omitted parent retirement, missing child cache custody, retained completed
child and reordered material reads failed their intended controls.

One real-server process per runtime used the same observer jar, CPUs4–7,
1–4GiB heap, real registered biomes and `ServerLevel` storage. Each row has
six compilation/GC-quiet samples of4,096 public calls after24 warmup batches.
Extra retry batches and all actual call counts are retained, not hidden or
assumed equal. Every sample's result matched; the scene stayed256 water,
256 stone and512 air cells. Current's native parent count matched the actual
public calls, and no parent/temperature child remained after each row.

| Actual public workload | Frozen ns/call | Pre-change ns/call | Current ns/call | Current/pre |
| --- | ---: | ---: | ---: | ---: |
| Cached precipitation | 6.05 | 34.39 | 109.86 | 3.194× |
| Cached cold-water freezing | 52.55 | 259.93 | 384.79 | 1.480× |
| Cached snow on stone | 133.32 | 326.80 | 517.18 | 1.583× |
| Frozen-biome precipitation misses | 139.37 | 310.78 | 513.98 | 1.654× |

Current/Frozen ratios are18.158×,7.322×,3.879× and3.688× respectively.
These are explicit temporary boundary/world-service debts, not final budget
passes. The extra native continuation round trips preserve genuine VM facts;
remaining exit work is direct consumption of shared native weather/temperature
from Rust-owned world services as those callers migrate. Do not claim the cost
is unavoidable or that this screen establishes gameplay/FPS performance.

Java allocation was zero per call except snow: Current/pre both72B, Frozen48B.
That includes the actual block survival callback and does not measure native
allocation. Sampled process peak RSS was1,022.3/1,156.1/1,172.3MB for
Frozen/pre/Current; all had zero JVM swap. These whole-process values do not
attribute the RSS difference to weather. No unbounded frame retention was
observed in the checked work. Owner: S33 whole-world-generation worker.

Receipts: `build/worldgen/weather/{synced-focused,portable-synced,controls-synced,server-screen-second}/`.
The earlier `36b5ae3b…` checks remain labeled the pre-sync image. A first screen
harness failed compilation on a checked exception before any benchmark ran;
its failed receipt is preserved, and only the harness was corrected.

## Publication verification

On the combined `993eafb5…` plus weather candidate above, the stable tiers
passed: 2,573 ordinary Java tests with six expected skips, 193 parity tests,
11 slow tests, and 3,108 Rust tests with nine ignored; zero failures.
The coherent release native image remained `1c230f26…` throughout.

Actual server comparisons generated 648 FULL chunks per runtime in each of
the straight and CARVERS-save/reload-resume phases across four dimensions.
Current matched the sealed pre-change runtime throughout. The three standard
dimensions matched original Frozen; the existing 162 Primordial differences
in each phase remain recorded. All six servers saved and exited successfully.
Failure rollback, cancellation, stock stage/binding, and player shutdown with
pending generation followed by each runtime's own save reopen also passed.
Source, class, native-image and immutable-baseline guards stayed unchanged.
These correctness checks do not establish gameplay or final performance
acceptance. Whole world generation remains in progress.

Receipts: `build/worldgen/weather/{stable-tiers,stable-worlds,full-straight,full-resume,failure-cancel,stock-binding,player-shutdown}/`.
