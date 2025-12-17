# Fabric API Stubs Required for Distant Horizons

## Core Event System (fabric-api-base) ✅ IMPLEMENTED
- `net.fabricmc.fabric.api.event.Event` ✅
- `net.fabricmc.fabric.api.event.EventFactory` ✅
- `net.fabricmc.fabric.impl.base.event.EventFactoryImpl` ✅
- `net.fabricmc.fabric.impl.base.event.ArrayBackedEvent` ✅
- `net.fabricmc.fabric.impl.base.event.EventPhaseData` ✅
- `net.fabricmc.fabric.impl.base.toposort.SortableNode` ✅
- `net.fabricmc.fabric.impl.base.toposort.NodeSorting` ✅

## Player Events (fabric-events-interaction-v0) ✅ IMPLEMENTED
- `net.fabricmc.fabric.api.event.player.AttackBlockCallback` ✅
- `net.fabricmc.fabric.api.event.player.UseBlockCallback` ✅

## Client Event Lifecycle (fabric-lifecycle-events-v1) ⏳ IN PROGRESS
- `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents` - needs MC 1.21.10 class name fixes
- `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents` - needs MC 1.21.10 class name fixes
- `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents` - needs MC 1.21.10 class name fixes

## Client Rendering (fabric-rendering-v1) ❌ NOT STARTED
- `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents`

## Client Networking (fabric-networking-api-v1) ❌ NOT STARTED
- `net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking`

## Server Event Lifecycle (fabric-lifecycle-events-v1) ⏳ IN PROGRESS
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents` - needs MC 1.21.10 class name fixes
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents` - needs MC 1.21.10 class name fixes
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents` - needs MC 1.21.10 class name fixes
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents` - needs MC 1.21.10 class name fixes

## Server Networking (fabric-networking-api-v1) ❌ NOT STARTED
- `net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry`
- `net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents`
- `net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking`

## Commands (fabric-command-api-v2) ❌ NOT STARTED
- `net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback`

## Entity Events (fabric-entity-events-v1) ❌ NOT STARTED
- `net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents`

## Progress Summary
- ✅ **Completed**: 9 stubs (Core Event System + Player Events)
- ⏳ **In Progress**: 7 stubs (Lifecycle Events - need MC 1.21.10 class name mapping)
- ❌ **Not Started**: 6 stubs (Rendering, Networking, Commands, Entity Events)
- **Total**: 22 stubs (9/22 = 41% complete)
