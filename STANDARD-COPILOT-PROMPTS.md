# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: Code review for random 10 issues
- I want you to inspect my repo and search for anything that could cause bugs or potential issues, code clarity problems, or simple performance issues, focusing on low hanging fruit first and only doing a few larger changes if you still have time. The frnsrc directory contains Minecraft source code and assets, so do not modify or refactor anything in frnsrc. Do not make any massive sweeping architectural changes anywhere in the repo; if you think a large architectural change is necessary, describe it and your reasoning in PR comments instead of implementing it. Your done state is finding and fixing at least ten concrete issues and documenting each fix in the PR description.
