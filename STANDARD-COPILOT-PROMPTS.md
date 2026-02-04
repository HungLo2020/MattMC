# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: add  a mob
- alright based on my AC-HOWTO-IMPLEMENT.md document i want you to completely and thoroughly read that entire document. based on that document i want you to completely and thoroughly implement the blobfish from AlexsMobs (source code for the mod in frnsrc/). you must ensure to copy over the textures and assets, add translations, etc.


## Prompt 2: migrate more stuff to vulkanic
- good work. with the knowledge gained and the first calls being migrated into vulkanic, i want you to know find more opengl calls outside of the vulkanic directory elsewhere in the project, and then properly route them to vulkanic for vulkanic to delegate to the proper backend, for now just opengl, i then need you to actually implement the necessary opengl calls if not already implemented. these should be basically 1:1 with what already exists, just migrated into the backend.