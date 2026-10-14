# Developer tooling

The `DevUtils/` root is reserved for tools explicitly requested there by the
user. Keep test and verification drivers in `DevUtils/tests/<subsystem>/`.
World-generation drivers and their Java agent helpers live in
`DevUtils/tests/worldgen/`; see the [world-generation guides](../world/levelgen/index.md)
for commands and prerequisites.

- [Developer Tips and Tools](DEV-TIPS-TOOLS.md): Git workflows for investigating changes.
- [Interim migration acceptance](MIGRATION-ACCEPTANCE.md): Frozen comparison allowances, local correctness and rolling desktop client checks.
- [Running tests quickly](TESTING.md): affected-test runs, Rust `suite` profile, Java `test`/`parityTest`/`slowTest` tiers, forks, cached Rust tests and memory-protected runs.
- [Native builds and running clients](NATIVE-BUILDS.md): atomic library staging, shared-build constraints and isolated verification.
- [Shared Agent Skills](AGENT-SKILLS.md): skill locations, invocation, and maintaining shared skills.
- [DH task admission and cancellation](DH-TASK-SCHEDULING.md): rejected-task retries, completion ownership, shutdown and native-resource cleanup.
