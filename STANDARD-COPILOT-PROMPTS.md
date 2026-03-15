# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: add  a mob
- alright based on my AC-HOWTO-IMPLEMENT.md document i want you to completely and thoroughly read that entire document. based on that document i want you to completely and thoroughly implement the blobfish from AlexsMobs (source code for the mod in frnsrc/). you must ensure to copy over the textures and assets, add translations, etc.

## Prompt 2: Vulkanic
- alright good work. i now want you to pick the next highest impact problem and implement solutions end to end to make our Vulkanic API and the game code call sites more agnostic to backend renderer. then you need to implement everything as needed in the actual backends for both opengl (if needed) and for vulkanic. remember that since vulkan is "closer to the machine" our call sites should be as well and opengl should be effectively "emulated" and we should move more in that direction to strengthen our api and improve everything. it is critical you create tests as you go and DO NOT CAUSE regressions!. after you are done verify your work helped and explain to me what you did, why you did it, and how it helps our goal. you should use VULKAN_BACKEND_READINESS.md as a working document and to keep track of how "close" we are to our goal.

For every session, your work should be judged by the following progress classes. 1: "Abstraction Improvement", where you remove OpenGL-shaped assumptions from shared/game code. Proof, fewer concrete GL casts, ids, or wrappers in production callsites. stronger backend-neutral API seam. Source guardrail test prevents regression. 2: Backend Implementation. The change moves a VulkanBackend path from fake/no-op/fail-fast to real Vulkan behaviour. Proof. Concrete Vulkan code path added. Targeted lifecycle/ validation tests added. Fail-fast hole or compatibility ship removed or reduced. 3: Runtime bootstrap progress. The change increases how far Vulkan can get during startup.
Proof: a specific startup milestone is now passed that was previously blocked. failure point moves later. diagnostics become more precise. 4: Parity improvement. The change makes Vulkan behavior closer to OpenGL behavior. Proof: a callsite now expresses backend intent instead of GL mechanism. a semantic mismatch with OpenGL path is removed. tests assert the same contract for both backends. 5: Debuggability improvement. The change makes future failures easier to localize. Proof: clearer readiness report. narrower failure mode. stronger validation or error reporting.

For each session you MUST: 1: explain EXACTLY what problem you are targeting, not just "Vulkan Readiness" something concrete. 2: Explain WHAT changed structurally and how its relevant and strengthening our Vulkanic API/ Vulkan Backend/ OpenGL backend/ actual Callsite usage. 3: what evidence PROVES this change is correct? tests, guardrails, narrower failure mode, or moved runtime blocker. 4: what remains unproven.

- i need you to do a COMPLETE AND COMPREHENSIVE READ ONLY audit of this project and its graphics abstraction layer Vulkanic and its two backends OpenGL and Vulkan. as it stands Vulkan backend doesnt work and OpenGL works perfectly. Why is this? what am i missing for Vulkan? how can i make the API and callsites that use it (should be ALL game code) stronger and better and more robust? report your findings here in chat

## Prompt 3: Audit Code Base
- i want you to do a comprehensive audit and review of my project looking for issues, dead code, poor design, maintainability, etc. you should ignore the frnsrc/ directory and the ERROR-LOG.txt for this PR. also DO NOT MAKE ANY CHANGES OR CReATE OR DELETE ANY FILES! PURELY CONDUCT YOUR REVIEW ADN REPORT YOUR FINDINGS HERE