# Developer Tips

This document contains helpful tips and tricks to improve your workflow as you develop and debug the codebase.

---

## Useful Git Features

### Git Blame

'git blame' is a Git command that tells you who last changed each line in a file (can also specify file range) and which commit introduced that change (plus the timestamp).

### Git Bisect

`git bisect` is a powerful debugging tool that uses binary search to identify the commit where a bug or issue was introduced. By marking a good commit and a bad commit, Git walks you step-by-step through intermediate commits, saving you time compared to checking every single one.

#### Key Commands:
- **`git bisect start`**: Begins a bisect session.
- **`git bisect good <commit>`**: Marks a commit as good (problem does not exist).
- **`git bisect bad <commit>`**: Marks a commit as bad (problem exists).
- **`git bisect skip`**: Skips the current commit if it cannot be tested.
- **`git bisect reset`**: Ends the bisect session, returning the repository to its original state.

#### Example:
1. Start bisect:
   ```bash
   git bisect start
   ```
2. Mark the bad commit:
   ```bash
   git bisect bad HEAD
   ```
3. Mark the good commit:
   ```bash
   git bisect good <commit-hash>
   ```
4. Test the selected commit, then mark it as `"good"`, `"bad"`, or `"skip"`:
   ```bash
   git bisect good  # or
   git bisect bad   # or
   git bisect skip
   ```
5. Once the culprit is found, reset:
   ```bash
   git bisect reset
   ```

Git will automatically narrow down the range of commits until it identifies the first bad one.

---

Explore and use these Git tools carefully to debug and resolve issues more efficiently! 🎯
