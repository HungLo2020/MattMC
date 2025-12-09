# Foreign Source Code Directory

## Purpose

This directory contains source code from **foreign projects, applications, and libraries** that are used **ONLY as a reference** within the MattMC project.

## Important Notes

⚠️ **Reference Only**: The code in this directory is **NOT compiled or linked** into the MattMC project. It serves purely as a reference for understanding implementation details, algorithms, or APIs used by external dependencies.

⚠️ **Original Licensing**: All source code in this directory retains its original licensing from the respective projects. Please refer to each project's license file or documentation for usage terms.

⚠️ **Do Not Modify**: Files in this directory should remain as close to their original form as possible to maintain accurate reference material.

## Usage Guidelines

### When to Add Code Here

Add foreign source code to this directory when:
- You need to understand the internal implementation of a library dependency
- You're debugging integration issues with external code
- You want to reference design patterns or algorithms from other projects
- You need to maintain compatibility with specific versions of external APIs

### When NOT to Add Code Here

Do not add code here if:
- It should be a proper Maven/Gradle dependency (use `dependencies` in `build.gradle` instead)
- You intend to modify and use it (fork the project properly instead)
- It's small enough to be understood from documentation alone

## Directory Structure

Organize foreign source by project:

```
frnsrc/
├── README.md                    # This file
├── <project-name>/             # Foreign project source
│   ├── README.md               # Notes about why this source is here
│   └── ...                     # Source files
└── ...
```

## Examples

Example scenarios where foreign source references are useful:

1. **Understanding Decompiled Behavior**: When working with Minecraft's decompiled code, having reference implementations from Mojang's open-source libraries (like Brigadier, DataFixerUpper) helps understand intended behavior.

2. **API Compatibility**: When integrating with libraries that have sparse documentation, having the actual source helps ensure proper API usage.

3. **Performance Optimization**: Studying optimized implementations from high-performance libraries can inform optimization decisions in MattMC.

## Maintenance

- Periodically review this directory to remove outdated references
- Document the version/commit hash of foreign source when adding it
- Consider removing sources if the project adds proper documentation or if the reference is no longer needed

## Legal Considerations

- **Never distribute** foreign source code without proper licensing compliance
- Keep clear attribution to original authors and projects
- When in doubt about licensing, don't add the code—link to the official repository instead
- This directory should be excluded from distributions (check `.gitignore` and distribution tasks)

---

*Remember: This directory is for learning and reference, not for incorporating foreign code into MattMC builds.*
