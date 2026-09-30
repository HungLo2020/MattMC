# Shared agent skills

Project skills have one source of truth in `.agents/skills/`. Claude Code's
`.claude/skills` is a relative directory symlink to `../.agents/skills`.

```text
.agents/skills/
├── say-hello/
│   └── SKILL.md
└── sync-and-resume/
    └── SKILL.md
.claude/skills -> ../.agents/skills
```

## Discovery locations

| Agent | Project directory | Personal directory |
| --- | --- | --- |
| Codex | `.agents/skills/` | `~/.agents/skills/` |
| Claude Code | `.claude/skills/` | `~/.claude/skills/` |

Codex scans project skill directories from the current directory up to the
repository root. Claude Code discovers project skills through `.claude/skills/`.
Both document support for symlinked skill folders. This repository links the
whole Claude skills directory so new shared skills are exposed through both
paths automatically. Personal directories are unaffected.

Sources: [OpenAI skill documentation](https://learn.chatgpt.com/docs/build-skills)
and [Claude Code skill documentation](https://code.claude.com/docs/en/skills).

## Say Hello

The `say-hello` skill instructs the agent to reply with exactly `hello`.
It uses the common `SKILL.md` format with YAML `name` and `description` fields;
automatic selection remains enabled.

- In Codex, invoke `$say-hello`.
- In Claude Code, invoke `/say-hello`.
- To try automatic selection, ask the agent to "say hello".

If Codex does not pick up the new skill, restart its session. In Claude Code,
run `/reload-skills` after adding a previously absent skills directory, or
start a fresh session. Discovery makes the skill available; automatic selection
still depends on the agent matching the request to its description.

## Sync and Resume

Use `sync-and-resume` when changes from another system need to be pulled into
the current branch while preserving ongoing local work.

- Codex: `$sync-and-resume`
- Claude Code: `/sync-and-resume`
- Natural request: "Pull the changes I pushed from my other system, resolve
  any conflicts while preserving both sides, and continue what you were doing."

The skill records the active task and repository state, preserves only the
local changes needed for recovery, integrates the configured upstream, reconciles conflicting changes,
and resumes the original task. It also checks interactions that Git can merge
without a textual conflict. It carries forward existing restrictions on
history rewriting and tests. It never backs up the whole repository. Creating
branch-history commits and pushing each require explicit user authorization;
pulling alone authorizes neither. Temporary stashes are permitted without
asking again, including Git's internal stash objects.

After pulling, it restores pending local changes and their staged/unstaged
state on top of the upstream changes, including untracked work. It uses
`git stash apply --index` and keeps its stash until restoration is verified.
Conflicts may require adaptations, which it explains. It leaves other stashes
alone and uses change-only patches or targeted copies when a stash is unsuitable.

It resolves routine conflicts without asking for confirmation. If the two
versions require incompatible behavior, or integration cannot satisfy the
user's Git constraints, it preserves the work and asks a focused question.
The workflow reduces the risk of losing work; it cannot guarantee that an
arbitrary combined implementation is correct without appropriate verification.

## Adding skills

Create `.agents/skills/<skill-name>/SKILL.md` with a lowercase, hyphenated name,
a description of when it applies, and concise instructions. Edit this canonical
copy; `.claude/skills/` resolves to the same files. Keep the relative symlink
intact when copying or checking out the repository.
