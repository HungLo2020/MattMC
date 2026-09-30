---
name: sync-and-resume
description: Pull upstream changes into the current branch while preserving local work, resolve integration conflicts, and resume the active task. Use when the user asks to pull or sync changes made on another system and keep working.
---

# Sync and Resume

Integrate the current branch's upstream changes while preserving the intended
behavior of both versions, then continue the user's active task. Creating or
editing this skill does not itself request a sync.

## Non-negotiable scope

- Never back up, archive, clone, or copy the entire repository for this workflow.
  Preserve only the changed files and metadata needed to recover this sync.
  Do not copy `.git/`, dependency trees, build outputs, or unrelated ignored data.
- Do not create branch-history commits or push unless the user explicitly requests that action.
  This includes WIP commits, merge commits, cherry-picks, and rebases that create
  replacement commits. Permission to pull or resolve conflicts is not permission
  to commit or push. Commit permission does not imply push permission.
- Temporary stashes are permitted for this workflow without asking again.
  Git's internal stash commit objects are an allowed exception; they do not
  authorize WIP commits, merge commits, history rewriting, or pushes.
- After pulling, restore the pending local work and its staged/unstaged state
  on top of the incoming changes. Preserve untracked files too. Conflicts may
  require adaptations; explain those exceptions rather than silently losing
  edits or leaving unrelated staging changes. Restoring local work does not
  mean undoing the upstream changes just pulled.

## Preserve the task and repository state

- Record the active objective, completed work, next steps, and user constraints,
  especially explicit authorization for commits or pushes and restrictions on
  history rewriting and testing.
  Syncing is an interruption of that task, not its replacement. If there is no
  active task, report the sync result without inventing new work.
- Inspect the repository root, current branch, configured upstream, HEAD,
  staged and unstaged changes, untracked files, and existing Git operations.
  Use the configured upstream; do not assume `origin/master`. Resolve a missing
  upstream or detached HEAD from explicit session context, otherwise ask.
- Preserve unrelated user changes as well as the agent's own edits. Inspect
  dirty submodules separately; a parent-repository diff or stash does not capture
  their uncommitted file contents.
  Pause file-writing work you started before taking a snapshot. If another
  process changes the checkout during integration, stop and reconcile its work.
- Do not abort or overwrite an existing merge, rebase, or cherry-pick unless
  continuing or recovering that operation is part of the current request.

## Inspect upstream and make recovery possible

1. Record the original HEAD and fetch the configured remote without modifying
   the working tree. Review incoming commits, changed paths, and ahead/behind
   relationships. A fetch failure leaves local work intact; report it and avoid
   retry loops. Fix an evident transient problem and retry only when justified.
2. Record a manifest of local changes and their staged/unstaged state, including
   partially staged files, untracked paths, file modes, and symlinks. Prefer a
   stash when temporary removal of edits is necessary. Give it a unique label,
   record the newly created stash's object ID, and verify that it captured the
   intended changes. Include untracked work when needed, using explicit paths
   if necessary to avoid sweeping in unrelated untracked directories. Never use
   `--all` indiscriminately or modify pre-existing stashes.
3. Prefer a direct fast-forward that leaves local edits in place when Git allows
   it; no stash is needed in that case. If a stash cannot represent the affected
   work, use change-only binary patches with full object IDs and targeted copies.
   Save the combined diff from HEAD plus separate staged and unstaged diffs;
   retain file metadata. Keep patch/copy recovery material outside the working
   tree. Verify it before moving or restoring any local file. Any scoped
   restore to HEAD must name only paths already captured; never reset the whole
   checkout. Move colliding untracked files to the recovery directory rather
   than deleting them. Leave non-colliding untracked files in place.
4. Account for incoming paths that collide with ignored files too. Preserve only
   the specific colliding files, not an entire ignored directory. If that would
   require a broad or expensive backup, stop before overwriting anything and
   explain the concrete obstacle. Do not silently expand the backup scope.

## Integrate deliberately

- Integrate the fetched upstream commit you inspected. Use a fast-forward when
  possible, such as `git merge --ff-only <fetched-upstream-commit>`. If upstream
  is already an ancestor of HEAD, there is nothing to pull; retain local commits.
- For diverged histories, preserve both tips and inspect the integration needed.
  Without explicit authorization to create commits, do not merge-commit, rebase,
  cherry-pick, or leave a speculative merge pending. Explain that a fast-forward
  is impossible and ask which history-changing action the user authorizes.
  If already authorized, follow repository policy: preserve published history
  and rebase only unpublished commits when rewriting is also permitted.
- Restore a stash with `git stash apply --index <recorded-object-id>` so both
  the working files and their staging are restored. Keep the stash through
  verification rather than popping it immediately. If applying it conflicts or
  restoring the index fails, inspect the resulting state and reconcile it against
  the saved versions; do not blindly apply it again or discard staging information.
- For patch-based recovery, reapply saved local changes against the updated base,
  using three-way application where appropriate. Retain patches until reconciled.
  A failed application may partially modify the tree; inspect it before retrying.
- If integration fails after work was stashed, restore the original pending work
  when safe before returning to the user. If an unresolved Git operation makes
  that unsafe, preserve and identify the stash and exact recovery state.
- Reconcile untracked-file collisions manually using the saved local copy and
  incoming tracked file. A rename, deletion, or API change can require adapting
  local work to the new structure rather than recreating the old file.
- Do not use forced checkout, `reset --hard`, `clean`, blanket `ours`/`theirs`,
  or force-push as conflict resolution. Do not change tracking or global Git
  settings just to get a pull to succeed. Push only when already authorized.

## Resolve meaning, not just conflict markers

For each conflict, compare the common base, pre-sync local version, and incoming
version, along with the active task and relevant callers/documentation. During
a rebase, patch, or stash application, confirm which revision each conflict side
represents rather than assuming the labels mean local versus remote.

Combine compatible behavior and adapt local changes to intentional upstream
refactors. Resolve routine conflicts autonomously. If the two sides require
contradictory product behavior and the user's priorities do not settle it,
preserve both versions in the recovery material and ask about that specific
decision. Do not silently discard a change or pretend incompatible behavior
can both be preserved.

Mark individually resolved files as needed to finish the Git operation. After
restoring uncommitted work, preserve its previous staged/unstaged division where
possible, using the saved diffs; explain any unavoidable staging changes.
Do not stage unrelated files with a blanket `git add .`.

## Verify and resume

- Confirm there are no unmerged entries or leftover conflict markers. Inspect
  the final diff and history against both recorded starting revisions. Verify
  upstream commits are integrated and account for every saved local change,
  including untracked files, deletions, renames, permissions, and symlinks.
- Review interacting changes even when Git merges cleanly: imports, signatures,
  build configuration, native interfaces, ownership, and documentation can still
  disagree. Run relevant builds/tests when authorized by the active task; report
  exactly what was checked and what remains unverified. Earlier validation does
  not establish correctness or performance of the combined version.
- Keep the stash or change-only recovery material through verification. Drop only
  the stash created by this sync, identified by its recorded object ID and current
  stash entry, once restoration is confirmed; never clear the stash list. Remove only recovery
  material created by this sync when preservation is confirmed; retain and
  identify it if anything is unresolved. If stopping mid-operation, report its
  state and the recovery locations instead of claiming the sync completed.
- Re-read any changed repository instructions and relevant docs, update affected
  documentation, and give a brief sync summary: incoming changes, resolutions,
  verification, and remaining recovery state. Then resume the recorded next
  step of the active task in the same turn when possible. A successful pull
  alone is not completion of the original task.
