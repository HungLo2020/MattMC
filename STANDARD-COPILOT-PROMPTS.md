# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: add  a mob
- alright based on my AC-HOWTO-IMPLEMENT.md document i want you to completely and thoroughly read that entire document. based on that document i want you to completely and thoroughly implement the blobfish from AlexsMobs (source code for the mod in frnsrc/). you must ensure to copy over the textures and assets, add translations, etc.

## Prompt 2: Vulkanic
- Continue migrating VulkanicAPI to support Vulkan. Read VULKANIC-MIGRATION.md to understand the goal and MIGRATION-PROGRESS to keep track of progress - it's your whiteboard showing what's done and what needs work. Update it as you progress. Pattern: Add CommandContext parameter to deprecated methods, implement in OpenGLBackend, update VulkanicAPI facade, migrate call sites, remove deprecated version. Test after each method. Document changes in MIGRATION-PROGRESS.md. Principles: API must work for both OpenGL and Vulkan, game code shouldn't need changes when Vulkan backend arrives, migrate incrementally, always test. Pick 5 more methods and continue the migration.

## Prompt 3: Audit Code Base
- i want you to do a comprehensive audit and review of my project looking for issues, dead code, poor design, maintainability, etc. you should ignore the frnsrc/ directory and the ERROR-LOG.txt for this PR. also DO NOT MAKE ANY CHANGES OR CReATE OR DELETE ANY FILES! PURELY CONDUCT YOUR REVIEW ADN REPORT YOUR FINDINGS HERE