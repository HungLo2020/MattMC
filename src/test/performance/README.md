# Performance Tests

Performance benchmarking tests isolated from regular unit tests.

## Running Chunk Save/Load Performance Tests

```bash
./gradlew runChunkPerformanceTest --no-build-cache
```

This runs comprehensive benchmarks measuring chunk serialization, disk I/O, and deserialization performance for various chunk complexities.

## Test Scenarios

- **Single chunks**: Empty, simple (4 sections), complex (8 sections), varied (12 sections)
- **Bulk operations**: 10, 50, and 100 chunks with mixed complexity

## Results

Results are logged to console with timing breakdowns for:
- Serialization time
- NBT encoding time
- Disk save time
- Disk load time
- Deserialization time

## Notes

- Performance tests are **not** included in regular `./gradlew test` or game build tasks
- Tests use 5 warmup iterations + 10 measured iterations for stable results
- Temporary test data is stored in `build/tmp` and cleaned up automatically
