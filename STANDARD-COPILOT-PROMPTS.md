# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: Code review for random 10 issues
- I want you to inspect my repo and search for anything that could cause bugs or potential issues, code clarity problems, or simple performance issues, focusing on low hanging fruit first and only doing a few larger changes if you still have time. The frnsrc directory contains Minecraft source code and assets, so do not modify or refactor anything in frnsrc. Do not make any massive sweeping architectural changes anywhere in the repo; if you think a large architectural change is necessary, describe it and your reasoning in PR comments instead of implementing it. Your done state is finding and fixing at least ten concrete issues and documenting each fix in the PR description.

## Prompt 2: Performance Code Review
- I want you to inspect my repo and search for 10 performance bottlenecks / unoptomized sections of code. Focus on low hanging fruit and things that will have the largest performance impact. Focus on things to reduce CPU, RAM, VRAM, GPU, and IO usage. Do not directly modify the code, i want you to find 10 of these and then reply to this PR with a summary of the issues, why they are a problem, how they can be optimized. For all of these from a player facing perspective all behaviour should be the same. Your done status is finding and documenting at least 10 issues. Expect me to reply with instructions to fix one or more of your findings. When you DO fix the issues i expect you to run my included performance tests in my project BEFORE and AFTER you make your changes and explain to me the impact each had.

## Prompt 3: Compare to Minecraft in frnsrc/
- I want you to inspect this repo and compare its overall architecture, subsystems, and patterns to the Minecraft Java code and assets in the frnsrc directory. Treat frnsrc as read-only reference code and do not modify or refactor anything inside frnsrc. Identify both large-scale and small-scale architectural similarities and differences (directory layout, module boundaries, rendering/input/world/asset systems, data flow, abstraction layers, etc.). Based on this comparison, propose a prioritized list of concrete changes that would make this project’s architecture closer to Minecraft’s where that would be beneficial, but explicitly call out any areas where my current implementation is equal or better and should be kept as-is. For each proposed change, explain what to change, why it matters, what Minecraft appears to do, and how risky or invasive the change would be. Your done state is producing at least ten clear, actionable architectural recommendations plus a short summary of the key divergence areas between this project and Minecraft.
