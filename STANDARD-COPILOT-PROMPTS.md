# COPILOT PROMPTS:

## HOTKEYS:
- SHIFT+ENTER: New Line from anywhere
- HOME: Beginning of line
- SHIFT+END: Select to end of line

## Prompt 1: add  a mob
- alright based on my AC-HOWTO-IMPLEMENT.md document i want you to completely and thoroughly read that entire document. based on that document i want you to completely and thoroughly implement the blobfish from AlexsMobs (source code for the mod in frnsrc/). you must ensure to copy over the textures and assets, add translations, etc.

## Prompt 2: Vulkanic
- Alright i need you to first do a COMPREHENSIVE READ ONLY AUDIT of the vulkanic api and abstraction layer and its respective opengl and vulkan backends and find the largest issues in terms of correctness for vulkan, performance, cleanliness, and overall structure with the goal of making this a high quality and proffessional abstraction layer and making the vulkan backend performant and first class. after you have identified the largest gaps i need you to run the performance and test harness to get a baseline reading of the current state of the project. keep track of the performance metrics for both opengl and vulkan runs. Then you need to pick the highest priority item from that audit and you need to implement AN END TO END SOLUTION, 100%. ENSURE NO REGRESSIONS by running the test and performance harness again. if your changes cause the project to not build or launch IT MUST BE FIXED PROPERLY. if the changes cause any more than a 2% performance regression for average fps or 1% lows for EITHER vulkan or opengl then you need to fix it or undo your changes and try again. after you have made changes that at LEAST dont hurt performance and help move us towards our goal by a measurable amount you MUST JUSTIFY TO ME WHAT YOU DID AND WHY. you must EXPLAIN HOW IT HELPS. and you MUST GIVE THE CONCRETE BEFORE AND AFTER PERFORMANCE METRICS. YOU MUST KEEP WORKING AND TRYING UNTIL YOU GET AT LEAST A 50% PERFORMANCE IMPROVEMENT TO VULKAN.


## Prompt 3: Audit Code Base
- i want you to do a comprehensive audit and review of my project looking for issues, dead code, poor design, maintainability, etc. you should ignore the frnsrc/ directory and the ERROR-LOG.txt for this PR. also DO NOT MAKE ANY CHANGES OR CReATE OR DELETE ANY FILES! PURELY CONDUCT YOUR REVIEW ADN REPORT YOUR FINDINGS HERE

-Act as an expert Principal Software Engineer performing a comprehensive, read-only code audit. Your goal is to identify the most significant issues that compromise the codebase's quality, maintainability, and performance.
Analyze the entire codebase and provide a prioritized list of the top 20 most critical issues. For each issue, provide:
1.  Issue Title: A brief, clear summary of the problem.
2.  Impact Analysis: A concise explanation of why it is a problem (e.g., "causes slow database queries," "increases risk of bugs," "makes new features difficult to add").
3.  Location: A specific example (file path and line number) where the issue can be observed.
Focus your audit on these key areas:
Architectural Flaws: Poor modularity, high coupling between components, circular dependencies, and violations of established design patterns.
Performance Bottlenecks: Inefficient algorithms, unoptimized loops, redundant database calls, memory-intensive operations, and blocking I/O.
Code Smells & Complexity: Overly complex functions/classes (high cyclomatic complexity), massive files, "god objects," and significant code duplication (violating DRY principle).