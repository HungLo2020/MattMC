---
name: sync-and-push
description: Sync a dirty branch with its upstream, preserve and reconcile local work, then create and push one commit containing that work on top of upstream. Use when the user requests both syncing and committing/pushing pending local changes, including changes made across multiple systems on the same branch.
---

# Sync and Push

Integrate upstream changes first, then publish the pending local changes as
exactly one new commit on top. Preserve the intended behavior of both versions.
Invoking this skill explicitly authorizes syncing, creating that commit, and a
normal push. Creating, editing, or discussing the skill does not invoke it.
For automatic selection, the user's request must authorize all three actions;
permission to pull alone is insufficient. Honor explicit session restrictions.

## Inspect and preserve

1. Pause file-writing work you started. Record the active task, user constraints,
   repository root, branch, upstream, original HEAD, status, staged and unstaged
   diffs, untracked paths, and existing Git operations. Use the configured
   upstream or an explicitly requested target; do not assume `origin/master`.
   Ask only if the target cannot be established from session context. Do not
   abort or overwrite an existing merge, rebase, or cherry-pick.
2. Inspect all pending work, including the user's edits. Unless the user scopes
   the commit, include the current local changes together. Identify intended
   untracked files explicitly; leave ignored outputs and unrelated concurrent
   work outside the commit. A parent-repository stash does not capture dirty
   submodule contents; resolve their scope before proceeding.
3. Fetch the configured remote. Record the exact upstream commit, incoming
   commits and paths, and ahead/behind counts. If HEAD equals upstream or can
   fast-forward to it, proceed. Existing unpublished commits or diverged history
   need a specific plan: do not silently push extra commits, squash or amend
   existing commits, create a merge commit, or rewrite history. Preserve the
   work and explain the obstacle if the requested one-commit result needs
   further authorization. A fetch failure leaves local work intact; report its
   cause without retry loops.
4. Record a manifest covering every pending change, its staging (including
   partially staged files), modes, and symlinks. When edits must be moved aside,
   create a uniquely labeled stash, include the intended untracked work, record
   its exact object ID, and verify the captured changes before proceeding.
   Temporary stash objects are allowed; WIP branch commits are not. Leave
   pre-existing stashes alone; never use `--all` indiscriminately.
5. Never back up or copy the entire repository, `.git/`, dependency trees, or
   build outputs. If a stash is unsuitable, retain only change-only binary
   patches and targeted copies outside the worktree, with staging and metadata
   recorded. Preserve specific colliding untracked or ignored files before
   integration; move them rather than deleting them. If preservation requires
   a broad backup, explain that concrete obstacle before overwriting anything.

## Integrate and reconcile

- Fast-forward to the inspected upstream commit, for example with
  `git merge --ff-only <upstream-object-id>`. When Git permits local edits to
  remain in place, a stash is unnecessary. When already current, retain the
  pending changes and proceed to review.
- Restore a stash using `git stash apply --index <recorded-object-id>`; retain
  it through verification instead of popping it. If application conflicts or
  index restoration fails, inspect the resulting state before doing anything
  else; do not blindly apply again. Preserve original staging during integration
  where possible, explaining conflict-driven exceptions. Final staging for the
  requested combined commit is intentional.
- Compare the common base, saved local version, and incoming version for each
  conflict. Confirm what each side represents, including during stash or patch
  application. Combine compatible behavior and adapt local edits to upstream
  renames, APIs, and refactors. Resolve routine conflicts autonomously. If the
  versions require contradictory behavior that session context cannot settle,
  preserve both and ask about that specific decision.
- Review interactions even without textual conflicts: callers, imports, build
  configuration, native interfaces, ownership, and documentation. Re-read
  changed repository instructions and relevant docs; update affected docs.
- Never use `reset --hard`, forced checkout, `clean`, blanket `ours`/`theirs`,
  or force-push. Do not change tracking, global settings, or credentials just
  to bypass an integration or push failure. If another process changes the
  checkout, stop and reconcile its work before continuing.
- If integration fails, restore the pending work when safe. Otherwise retain
  and identify the stash or patches and exact Git state; do not claim completion.

## Verify, create one commit, and push

1. Confirm upstream is integrated, no unmerged entries or conflict markers
   remain, and every saved local change is accounted for. Review the complete
   diff against the integrated tip, including deletions, renames, file modes,
   and symlinks. Verify both sides' intended behavior, not just clean Git status.
2. Run relevant builds/tests when authorized by the active task and checks
   required by repository instructions, including documentation checks after
   doc edits. Report what actually ran; earlier results do not verify the
   combined version or establish its performance. Do not start arbitrary
   project-wide testing or unrelated cleanup.
3. Before committing, fetch again to check for remote movement. If upstream
   advanced and can still fast-forward, repeat preservation, integration,
   reconciliation, and affected checks while local work is uncommitted. Do not
   loop indefinitely if another system keeps updating the branch.
4. Stage the reviewed, authorized paths explicitly, including deletions and
   intended untracked files. Avoid blanket `git add .`. Inspect the staged diff,
   then create one descriptive commit with the combined local work. Do not
   create intermediate commits or amend an existing commit. Verify the new
   commit's parent is the integrated upstream tip, its contents match the
   reviewed changes, and it is exactly one commit ahead. If no local diff
   remains after integration, report that no new commit was needed; never make
   an empty commit merely to reach a count.
5. Push normally to the established remote branch, using an explicit refspec
   when appropriate. Never force-push. If a remote race rejects the push after
   the commit was created, preserve that commit and explain the specific
   integration needed. Do not create a second commit, merge, or rewrite it
   without authorization for that recovery. On authentication or workflow-scope
   rejection, retain the local commit and report the reason; do not remove
   intended changes or alter credentials to bypass the rejection.
6. Verify the remote branch points to the pushed commit, for example with
   `git ls-remote`, and inspect final status. If it advanced again, confirm the
   published commit is included before reporting success. Do not mistake a
   cached tracking ref for remote verification.
7. Once preservation and publication are verified, drop only this workflow's
   stash, identified by its recorded object ID and current entry, and remove
   only its own recovery material. Never clear the stash list. Retain and
   identify recovery material when anything remains unresolved.

Report the incoming changes, conflict resolutions, verification performed,
commit hash, destination, and remaining local or recovery state. Resume prior
work only when the user's request includes continuation. Creating the skill
itself must leave all existing pending work uncommitted and unpublished.
