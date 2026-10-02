# Safe continuation and source synchronization

## Working branch and publication policy

Prepare wiki changes on `docs/wiki-expansion`. The source-of-truth default branch is currently `master`; resolve the actual default branch again on each run. Pull changes **from** the default branch into the wiki branch, preserving both histories. The standing instruction issued on 2026-10-02 UTC authorizes promoting every completed, validated coherent wiki batch, including the coordinated three-hour review, back into `master`. No separate manual approval is needed for those documentation promotions.

The initial one-time master fast-forward to `239a8cb570ae75443f9d7865d3caaa1b239a4300` preceded this standing instruction. The newer instruction supersedes the former branch-only publication restriction.

Author only wiki documentation, monthly changelog, and necessary navigation. Preserve incoming default-branch code through real source merges; do not author gameplay, workflow, repository-setting, release-tag, or external-tracker changes here. The existing master-push Wiki Pages deployment is an expected consequence of an authorized documentation promotion; do not change deployment settings or trigger unrelated releases.

## Start a batch

1. Ensure no other wiki writer is active. If a run is in progress, skip mutation and report that it owns the branch. Do not run overlapping writers.
2. Read [the checkpoint](coverage-plan.md), fresh repository instructions, and current remote tips. Preserve all existing local and remote edits.
3. Fetch the default branch and wiki branch. Record both exact SHAs and their merge base. Start from the current wiki tip, never from a stale local copy or an older checkpoint.
4. Reinspect workflow triggers before any branch update. At the initial snapshot, Wiki Pages deploys only on `master`; release workflows are manual or reusable. Do not assume that remains true after a sync.

The coordinated three-hour review covers wiki, monthly changelog, and issue tracking. Continuous wiki work may remain active between reviews. At review time, first ask the active writer for its published tip, source checkpoint, pending batch, and verified issue evidence; do not start a second writer. The documentation owner incorporates any verified changelog evidence in its next bounded batch. Group routine user-facing progress while preserving the exact per-batch mutation ledger.

## Sync without rewriting history

- If the default tip is an ancestor of the wiki branch, no source sync is needed.
- If a fast-forward is possible, advance only the wiki branch to the inspected source tip.
- If diverged, perform a real three-way Git merge of the inspected default tip into the wiki branch, preserving both parents and all branch documentation. Never replace the branch tree wholesale with the default tree.
- Use an ordinary merge commit for published diverged history, not a rebase or force-push. Stop and report conflicts rather than discarding either side. Do not overwrite another writer's changes.
- Read the repository's `sync-and-resume` skill where available. For an existing working checkout, follow its change-only preservation steps; never discard unrelated staged, unstaged, or untracked work.

### Connector-based publication fallback

When Git fetch works but authenticated Git push is unavailable, GitHub's Git-data tools can publish a verified local result:

1. Compute the real merge locally with Git, inspect it, and validate documentation.
2. Build a GitHub tree using the current wiki tree as its base and only the verified resulting changed entries. Preserve all unchanged files, modes, symlinks, and deletions exactly.
3. Create a commit with the current wiki tip as first parent. For a source merge, add the inspected default tip as the second parent. A two-parent commit alone is not a merge algorithm: the tree must come from the actual three-way merge.
4. Re-read both remote tips before publishing. If either changed, reconcile and redo validation rather than publishing a stale result.
5. First update `docs/wiki-expansion` with `force=false`. Verify its exact remote commit and changed paths, then follow the guarded default-branch promotion below. An uncertain update must be checked before any retry.

## Promote a completed batch

1. Finish a coherent batch and all required documentation validation. Re-read both remote tips and preserve concurrent edits; if master advanced, merge it into the wiki branch and repeat affected checks before promotion.
2. Verify the complete wiki-versus-current-master tree difference contains only authorized documentation/navigation changes. Incoming code must match the actual source branch; never replace newer user code with an older tree.
3. Inspect current workflows and branch requirements. Expected Wiki Pages build/deployment is allowed; stop for conflicting changes, unexpected publishing side effects, or an unmet required check. Do not relax protection or permissions.
4. When the current master is an ancestor of the validated wiki tip, fast-forward master to that exact tip using `force=false`. If histories diverge, first perform the real source merge described above. Never force-push or rewrite published history.
5. Read master back and verify the promoted SHA. Inspect the exact Wiki Pages run and confirm its build/deployment result; a successful ref update alone is not a successful live deployment.
6. Coordinate one active publisher and avoid overlapping deployments. Wait for the prior expected deployment to reach a terminal state before the next promotion, while continuing isolated research/drafts. Report a deployment failure and investigate within the authorized documentation scope.
7. Record exact old/new SHAs, merge or fast-forward method, paths, checks, and workflow links. Keep routine catch-up updates internal unless help is needed; provide the requested consolidated three-hour report and report broad catch-up completion only when it is actually established.

## Expand, validate, and report

Pick a coherent small batch from the coverage plan. Review active implementation, add useful content, and keep directory indexes complete. Do not generate thousands of generic stubs or import upstream claims wholesale.

Keep `docs/gameplay/blocks/Blocks.md` alphabetical by displayed block name, with exact registry IDs to distinguish duplicate names. Maintain separate material/use category pages under `blocks/catalog/`; each registered ID belongs in the alphabetical directory and one catalog list. Reconcile direct declarations, ResourceKey constants and helper-created registrations when source changes. Follow method references such as `Blocks::register` into helpers and expand every registration call; a regex over fields typed `Block` misses record/factory families. Deduplicate exact IDs against the active registration path, retain immutable source evidence, and distinguish source inventory from a runtime registry dump. The current correction counts 1,211 direct fields plus 24 helper-created Copper forms, not merely the direct fields. Link existing placed-block guides conservatively; mark missing articles explicitly and never treat inventory coverage, a related-family link, or an item stub as completed block-behavior coverage. Preserve meaningful family articles rather than creating duplicate variant stubs.

For ported methods, verify the current base-class signature and actual call path before describing a method body as active behavior. An old overload with a similar name can compile without overriding the current callback. Trace helper calls through their actual state changes too; a method name such as a break/conversion helper does not establish that the returned item changes in the current implementation. Treat source presence, active dispatch, data registration, and runtime tests as separate evidence.

When reporting resource counts, enumerate the exact active loader paths. Keep singular `recipe/` and `loot_table/` resources separate from advancement/tag files, loader-specific or legacy paths and nested optional data packs. A broad negative search may inspect those additional scopes, but its total must not be labeled as the active base registry. Record source-path counts separately from runtime-loaded counts. Some plural `recipes/` resources remain active through dedicated systems: [TaCZ reads its own recipe files](https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L150), and its [workbench menu consumes that list](https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L51-L56). Being outside the ordinary RecipeManager path does not prove a resource is unused.

Run the required documentation checker and strict build locally. Record what passed, failed, or could not run; source review is not gameplay verification. Recheck the exact published SHA and any workflow runs. A wiki-branch push alone is not a live deployment; verify the subsequent master promotion and its deployment run.

Update the checkpoint with the source SHA, pages changed, evidence gaps, tests, and next priority. Summarize every mutation in the requesting conversation, including source merges, all created/updated page paths, commits, and verification results. Scheduled and continuous continuation should use one writer, complete each coherent batch, and apply the standing promotion procedure.

## Monthly changelog

Maintain completed work in `docs/changelog/changelog/M.YYYY.md`, using the existing month/year naming. Link each new month from both `docs/changelog/changelog/index.md` and `docs/changelog/CHANGELOG.md`.

- Append one short, single-line bullet for a meaningful completed outcome; group related page work rather than listing every page, edit, check, or issue created.
- Link the actual GitHub issue when the completed work is demonstrably related to it. Verify the association from the task, issue, PR, or commit; a similar subject alone is not evidence. Include a commit/PR link when useful for completion evidence.
- An issue filed, plan written, or bug discovered is not an implemented fix. If recording a completed documentation outcome about it, say that it was documented, not repaired.
- Separate work already landed on `master` from work committed only on `docs/wiki-expansion`. Do not imply that a branch commit is released, merged, or deployed. After the authorized promotion, reconcile existing status wording against the verified master commit instead of duplicating outcomes. The standing policy covers future completed wiki batches, but deployment success is still a separate checked result.
- Read the current month before appending and avoid duplicate outcomes. Verify completion dates at month boundaries; do not invent missing monthly history or shift older entries solely from an unreviewed timestamp conversion.
- Keep this wiki branch as the single changelog writer while it owns the documentation work. Tracker updates can provide verified issue/completion evidence; they must not start a competing branch or default-branch writer.
- Validate indexes and links with the normal wiki check/build. Include the monthly changelog path in the batch's mutation report.

Historical gaps (including missing August and September 2026 files) require a separate history reconciliation before entries are written. Existing earlier entries are preserved until their dates and facts are verified.
