# Architectury Porting Notes

This branch starts the migration from a single NeoForge project to a multi-loader layout.

## Current shape

- `neoforge/` contains the existing NeoForge implementation with its original ModDev build.
- `common/` is the target for platform-neutral code.
- `fabric/` is an Architectury/Fabric skeleton that currently only calls the shared common entry point.

## Recommended extraction order

1. Move rule data classes, JSON loading helpers, and validation into `common/`.
2. Move pure exchange calculation into `common/`.
3. Keep block entities, menus, networking, commands, capabilities, and client screens in loader-specific projects until their abstractions are clear.
4. Add small platform services for logging, config paths, networking, and item storage only when shared code actually needs them.
5. After `common` owns the stable logic, convert `neoforge/` from ModDev to the Architectury Loom NeoForge setup and wire it to `common`.

## Version pins

The template is pinned for Minecraft 1.21.1 first. Upgrade versions in `gradle.properties` before attempting a 1.21.x or 26.x port.
