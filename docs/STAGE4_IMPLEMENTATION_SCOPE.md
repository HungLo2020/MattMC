# Stage 4 Implementation - Scope and Status

## Stage 4 Objective
Apply the DrawCommand + RenderBackend pattern to item and UI rendering, same as was done for chunks in Stage 3.

## Requirements from RENDERINGREFACTOR.md
1. Identify item rendering and UI rendering entry points
2. Extract "logic-only" parts (no GL calls)
3. Route draw calls via RenderBackend
4. Remove direct GL calls from item/UI logic classes
5. After Stage 4: all "normal" rendering (world, items, UI) goes through RenderBackend

## Implementation Status

### Core Abstractions - COMPLETED ✅
- [x] Create UIRenderLogic class (no GL calls)
- [x] Create ItemRenderLogic class (no GL calls)
- [x] Extend OpenGLRenderBackend for 2D quads and UI
- [x] Add UI quad registry and rendering support

### Component Refactoring - IN PROGRESS
- [x] CrosshairRenderer - ✅ REFACTORED to use backend
- [ ] HotbarRenderer - route through backend
- [ ] ItemRenderer - route through backend  
- [ ] DebugInfoRenderer - route through backend
- [ ] CommandUIRenderer - route through backend
- [ ] Other UI renderers as needed

### Testing - IN PROGRESS
- [x] Tests for UI rendering logic (6 tests in UIRenderLogicTest)
- [x] Tests for item rendering logic (5 tests in ItemRenderLogicTest)
- [x] Tests for CrosshairRenderer (5 tests demonstrating pattern)
- [ ] Integration tests for more UI components

### Current Status (Latest Commits)
- ✅ Created UIRenderLogic and ItemRenderLogic classes (logic without GL)
- ✅ Extended OpenGLRenderBackend with UI quad support
- ✅ **CrosshairRenderer REFACTORED** to use backend architecture
  - Supports both new backend path and legacy fallback
  - UIRenderer updated to use backend when available
  - 5 new tests demonstrating the pattern
- ✅ Backend submit() method extended to handle UI render pass
- ✅ All 139+ tests pass
- ⏳ Next: Continue refactoring other UI components (Hotbar, DebugInfo, etc.)

## Note on Scope
Stage 4 as specified involves refactoring ALL item and UI rendering. This is extensive work affecting many files. This document tracks progress through incremental implementation.
