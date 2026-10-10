# DH render columns

Backend-independent compact-column reduction. It owns numeric reduction policy
and bounded worker scratch, never Java objects, GPU resources or presentation.
Start with the [developer guide](../../../../../docs/development/rendering/RUST-DH-RENDER-COLUMNS.md).

- `reduction.rs`: node ordering, merge/erase/forced reduction, water and material companions.
- `sort.rs`: Frozen's exact fastutil tie permutation (Apache2.0 attribution retained).
- `ffi.rs`: bounded borrowed CPU spans, native decline and direct publication.
- `tests.rs`: actual Frozen replay, alias/failure controls and independent worker scratch.

The fastutil sort adaptation retains its source attribution and the
[bundled Apache2.0 license](../../../resources/META-INF/licenses/fastutil-sort-LICENSE.txt).
