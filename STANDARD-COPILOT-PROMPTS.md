# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: Code review for random 10 issues
- I want you to inspect my repo and search for anything that could cause bugs or potential issues, code clarity problems, or simple performance issues, focusing on low hanging fruit first and only doing a few larger changes if you still have time. The frnsrc directory contains Minecraft source code and assets, so do not modify or refactor anything in frnsrc. Do not make any massive sweeping architectural changes anywhere in the repo; if you think a large architectural change is necessary, describe it and your reasoning in PR comments instead of implementing it. Your done state is finding and fixing at least ten concrete issues and documenting each fix in the PR description.

## Prompt 2: Performance Code Review
- I want you to inspect my repo and search for 10 performance bottlenecks / unoptomized sections of code. Focus on low hanging fruit and things that will have the largest performance impact. Focus on things to reduce CPU, RAM, VRAM, GPU, and IO usage. Do not directly modify the code, i want you to find 10 of these and then reply to this PR with a summary of the issues, why they are a problem, how they can be optimized. For all of these from a player facing perspective all behaviour should be the same. Your done status is finding and documenting at least 10 issues. Expect me to reply with instructions to fix one or more of your findings.
