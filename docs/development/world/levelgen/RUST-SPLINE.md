# Native terrain splines

Terrain spline evaluation is a private density plan under
`world/level/levelgen/density/spline/`. It binds the Java chunk's coordinate caches
to an immutable curve program. Folded-ridge and other supported coordinate
transforms execute in the same native call as the curve. Java retains graph
mapping, bounds, serialization and chunk/cache ownership.

## Compatibility and ownership

- Keep every spline intermediate at Java `float` precision. Coordinate arithmetic
  remains `double` until the original conversion to `float`. Do not introduce FMA
  or rearrange operations, including zero-derivative extrapolation.
- Preserve interval selection at knots, signed zero and NaN behavior. Native
  validation bounds array accesses, recursion and expanded evaluation work.
- Only exact built-in flat caches covering the requested point can bypass Java
  coordinate calls. Cache misses, custom contexts, unsupported coordinates and
  custom providers retain the original traversal. No speculative extension calls.
- Canonical density graphs remain unchanged. Private plans assume their source
  curves are immutable after binding, like other compiled density plans. Rebuild
  graphs/plans when changing settings; do not mutate exposed spline arrays in place.
- Numeric templates can be shared across mapped graphs; coordinate bindings and
  cache arrays stay with their owning chunk. Weak graph keys never retain chunks
  through their template values. Stateful visitors cannot reuse incompatible axis
  bindings. For the built-in chunk visitor, register only the surviving canonical
  graph in the template cache; registering discarded mapped graphs causes allocation
  and weak-reference cleanup overhead. This visitor maps each shared pure coordinate
  once; arbitrary visitors keep their original calls and order. Bounds still use
  the original Java calculation.
- Rust borrows Java-owned native allocations only for the duration of a call.
  Small bounded plans use critical downcalls without pinning Java heap arrays;
  larger plans use ordinary downcalls. Rendering is outside this subsystem.

## Verify a change

```sh
python3 DevUtils/tests/worldgen/VerifyRustSpline.py
```

This builds the release library and runs only focused spline tests. It checks
that `CubicSpline.java` matches the pinned original revision, then compares nested
curve outputs, coordinate transforms, custom-call behavior and seeded chunk-bound
splines. It also exercises concurrent readers and both ordinary and critical
downcalls. NaNs compare using Java's canonical NaN bits; other float results include
signed-zero bit comparisons. The baseline is the original Java spline evaluator
with the project's existing coordinate evaluators unchanged on both sides.

Timings compare actual density-function calls over the 25 quart-grid points used
by a chunk flat cache, with identical curves and inputs. Steady evaluation includes
coordinate access, transforms, the native call and result consumption. The
lifecycle case additionally maps the graph and binds the evaluator for each group
of 25 calls. Initial registry loading and one-time shared program compilation are
outside the warmed timings. These are subsystem measurements, not FPS or whole
chunk-generation claims.

Acceptance requires three alternating Java/native process pairs, stable warmup,
zero reported compilation during accepted rounds, and at least 5% less time in
every pair and the upper bootstrap confidence bound. Raw results and source/library
hashes are saved under `build/spline-migration/acceptance/`. Keep unsuccessful
measurements visible; a faster isolated kernel does not establish caller-level
acceptance.

## Verified result — 2026-09-30

On Ryzen 5 5600G, OpenJDK 25.0.4.1, release Rust, pinned CPU 2, all six cases
passed three alternating process pairs. Times below are medians of process
medians, in ns per evaluation; setup is amortized over 25 calls per curve.

| Settings / workload | Original Java | Native including boundary | Less time |
| --- | ---: | ---: | ---: |
| Overworld / evaluation | 104.7 | 56.0 | 46.5% |
| Overworld / setup + evaluation | 1,203.6 | 574.7 | 52.3% |
| Large Biomes / evaluation | 124.7 | 55.0 | 55.9% |
| Large Biomes / setup + evaluation | 1,306.1 | 617.2 | 52.7% |
| Amplified / evaluation | 107.9 | 55.8 | 48.3% |
| Amplified / setup + evaluation | 1,354.0 | 618.1 | 54.4% |

The smallest paired reduction was 44.3%; every upper 95% bootstrap time-ratio
bound was below 0.56 (the acceptance limit is 0.95). Accepted rounds reported
zero JIT compilation. Initial shared compilation remains excluded as described above.

Parity passed 600,000 randomized curve cases, 200,000 concurrent curve reads,
100,000 Java-coordinate arithmetic comparisons, 2,000 ordinary-downcall curve
comparisons, and 69,480 seeded terrain queries across three settings, five seeds
and four chunk origins. Additional checks cover invalid plans, custom visitors,
providers and cache misses. These are tested coverage, not an exhaustive proof
over every possible graph or seed. The Java curve oracle matches revision
`ffb5fd3949bd346663b7378f6a57528ac862538c` byte for byte.

Raw passing evidence is in `build/spline-migration/acceptance/`. Earlier setup
regressions remain in `rejected-shared-mapping/` and `rejected-canonical-mapping/`
under the same parent; those runs did not establish acceptance.
