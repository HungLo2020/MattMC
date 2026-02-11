# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: add  a mob
- alright based on my AC-HOWTO-IMPLEMENT.md document i want you to completely and thoroughly read that entire document. based on that document i want you to completely and thoroughly implement the blobfish from AlexsMobs (source code for the mod in frnsrc/). you must ensure to copy over the textures and assets, add translations, etc.

## Prompt 2: Vulkanic
- alright good work. i need you to continue working on this migration to a vulkan compatible API. replace deprecated methods and classes with new ones that will work with vulkan in the future but dont implement vulkan backend yet just make sure the opengl backend works with the new api and replace all original call sites for the deprecated methods with the new methods in the game code and then remove the deprecated methods. keep the VULKAN-COMPAT.md up to date with your work. you must do at least 5 methods. ensure any work you do will actually move us towards the goal of getting closer to being able to support vulkan in this project
