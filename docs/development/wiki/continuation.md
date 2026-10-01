# Safe continuation and source synchronization

## Branch boundary

Work only on `docs/wiki-expansion`. The source-of-truth default branch is currently `master`; resolve the actual default branch again on each run. Pull changes **from** the default branch into the wiki branch. Never update, push, merge, or open a publication request into `master` or `main` without a new explicit instruction.

Scope is wiki documentation and necessary wiki navigation only. Do not modify gameplay code, workflows, repository settings, release tags, published Pages, or external issue trackers in this workflow.

## Start a batch

1. Ensure no other wiki writer is active. If a run is in progress, skip mutation and report that it owns the branch. Do not run overlapping writers.
2. Read [the checkpoint](coverage-plan.md), fresh repository instructions, and current remote tips. Preserve all existing local and remote edits.
3. Fetch the default branch and wiki branch. Record both exact SHAs and their merge base. Start from the current wiki tip, never from a stale local copy or an older checkpoint.
4. Reinspect workflow triggers before any branch update. At the initial snapshot, Wiki Pages deploys only on `master`; release workflows are manual or reusable. Do not assume that remains true after a sync.

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
5. Update **only** `docs/wiki-expansion` with `force=false`. Verify the remote ref equals the intended commit and inspect its changed paths. An uncertain update must be checked before any retry.

## Expand, validate, and report

Pick a coherent small batch from the coverage plan. Review active implementation, add useful content, and keep directory indexes complete. Do not generate thousands of generic stubs or import upstream claims wholesale.

Run the required documentation checker and strict build locally. Record what passed, failed, or could not run; source review is not gameplay verification. Recheck the exact published SHA and any workflow runs. No branch push should be described as a live wiki deployment.

Update the checkpoint with the source SHA, pages changed, evidence gaps, tests, and next priority. Summarize every mutation in the requesting conversation, including source merges, all created/updated page paths, commits, and verification results. Scheduled continuation should run one batch at a time and retain this branch boundary indefinitely.
