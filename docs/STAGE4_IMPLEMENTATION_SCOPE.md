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

### Core Abstractions - IN PROGRESS
- [ ] Extend DrawCommand to support UI/2D rendering
- [ ] Extend OpenGLRenderBackend for 2D quads and UI
- [ ] Create UIRenderLogic class
- [ ] Create ItemRenderLogic class

### Component Refactoring - PLANNED
- [ ] HotbarRenderer - route through backend
- [ ] ItemRenderer - route through backend  
- [ ] CrosshairRenderer - route through backend
- [ ] Other UI renderers as needed

### Testing - PLANNED
- [ ] Tests for UI rendering logic
- [ ] Tests for item rendering logic
- [ ] Integration tests

## Note on Scope
Stage 4 as specified involves refactoring ALL item and UI rendering. This is extensive work affecting many files. This document tracks progress through incremental implementation.
