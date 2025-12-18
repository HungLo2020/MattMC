This directory contains the native files of libraries that were modified for relocation. They will be copied from here during the normal build steps.

Before adding/updating a library, make sure you have Python 3.8+ installed and check the instructions below.

How to add a library's natives:

1. In `build.gradle`:

- Make sure the target package is the same length or shorter (untested) than the source package. Underscores in native methods will be mapped to `_1` so account for that as well.
- Exclude the native files and add them as `relocateNative` (see example).

Example:

```groovy
relocate "org.sqlite", "dh_sqlite", {
    exclude "org/sqlite/native/**"
}

transform(NativeTransformer) {
    // NativeTransformer configuration
    rootDir = project.rootDir

    // Replace native strings, e.g. used in calls back to Java
    relocateNative "org/sqlite", "dh_sqlite"
    // Rename native methods used when calling from Java
    relocateNative "org_sqlite", "dh_1sqlite"
}
```

How to update a library's natives:

1. Delete the library's folder in cache/.
2. It will repopulate during the next build.

Why does this step exist?

- Native files are not handled by the shadow plugin correctly.
- I preferred it as a more streamlined approach, although a bit hacky.
- Possible alternatives:
    - Use edited libraries' source code: although more straightforward, it requires maintaining and updating the repositories for the libraries being added
    - Interfacing with the necessary libraries directly: an absolute mess for technical reasons

What are libraries used?

- LIEF: for fixing binaries after replacing strings
- apple-codesign: for re-signing Mac binaries, since their signatures get invalidated after previous steps
