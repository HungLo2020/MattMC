# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompts:

good work. can you do a thorough investigation into this project and find the next most important thing that needs done to improve our Vulkan correctness and architecture. our goal is complete and total parity between opengl and vulkan and a solidly and cleanly implemented and accurate vulkanic architecture. there are currently still issues with accuracy on vulkan, primarily with shaders. you need to find an issue and you must PROVE its an innacuracy via diagnostics, logging, or any means necessary. then you must report your findings. DO NOT MAKE CHANGES YET. IT IS NOT GOOD ENOUGH TO PURELY IMPROVE AUDITING OR LOGGING OR DIAGNOSTICS. YOU MUST ACTUALLY FIND AND FIX AT LEAST ONE CORRECTNESS ISSUE YOU CAN PROVE IS A CORRECTNESS ISSUE.

good work. vulkan + shaders + dh still has many graphical issues. i want you to now systematically go through the project and add logging and diagnostics as necessary. then run the tests, specifically opengl + shaders and vulkan +shaders to find discrepencies on any data being fed into the vulkan backend compared to the opengl backend and then how that data is being consumed, its output basically. again you are looking for areas where you can PROVE there is a correctness issue on vulkan. YOU MUST PROVE ITS AN ACTUAL ISSUE. continue running tests and adding diagnostics and logging as needed to complete to find and prove an issue is legit.  once you have found one design and completely and thoroughly implement a COMPLETE SOLUTION to the problem, run all 4 test cases to verify your work had ZERO regressions, and give me a report here.

so whats our next step here to continue the rust migration? my expectations are that call overhead stays minimal, performance should be as high as possible, at least on par with java, and that the java surface area shrinks over time. for this session the goal is completely eliminating and removing AT LEAST 1 java class file by completely migrating its functionality into rust. YOU CANT JUST DELETE A JAVA FILE AND MOVE ITS CODE INTO ANOTHER JAVA FILE. i expect the rust architecture and performance to be at least equivalent to that of the java its replacing. Stay focused on the Meshing pipeline.

alright dont make any changes dont run any scripts yet. just based on the implementations we have here... do think this rust implementation is better than the java? worse? or about equal? give me numbers.

good work. i want you to first run the meshing test for a baseline. then i want you to completely and throughly cleanly and efficiently implement these next recomended steps. do not take shortcuts. ensure the meshing test is up to date in both the current project and the old frozen java repo. before you are finished i expect you to run the test and report back with the results and the delta.