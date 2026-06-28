# Architectury Porting Notes

This branch starts the migration from a single NeoForge project to a multi-loader layout.

## Current shape

- `neoforge/` contains the existing NeoForge implementation with its original ModDev build.
- `common/` is the target for platform-neutral code.
- `fabric/` is an Architectury/Fabric skeleton that currently only calls the shared common entry point.

## Recommended extraction order

Progress: Steps 1 and 2 largely complete.

**common/** now owns:
- `ExchangeRule`, `ExchangeRuleComponents` — rule data model and component matching.
- `ExchangeRuleParser` — JSON parsing, validation, rule matching, serialisation/deserialisation (extracted from NeoForge `ExchangeRecipeManager`).
- `ExchangeCalculator` — pure calculation helpers: `getMaxExchanges`, `calculateConsumedItems`, `createNonNullList`.
- `ExchangeStrategy` — strategy interface (concrete implementations remain in NeoForge due to `applySellingPriceBoost` dependency).

**neoforge/** retains:
- `ExchangeRecipeManager` — resource reload listener, event bus, external config loading (delegates parsing/validation to common).
- `ExchangeManager` — `performExchange` orchestration, `applySellingPriceBoost` (NeoForge attributes + mod compat).
- Strategy implementations (`CoinSimpleStrategy`, `ItemSimpleStrategy`, etc.) — depend on `applySellingPriceBoost` and `DynamicPricingManager`.
- `DynamicPricingManager` / `PricingData` — use `ServerLifecycleHooks` and `PacketDistributor`.
- Block entities, menus, networking, commands, client screens, compat modules.

Remaining work:
1. Create platform abstraction for `applySellingPriceBoost` (attributes + EclipticSeasons compat) to move strategy implementations to common.
2. Create platform abstraction for `DynamicPricingManager` (server lifecycle + networking) to move to common.
3. Convert `neoforge/` from ModDev to Architectury Loom NeoForge setup.

## Version pins

The template is pinned for Minecraft 1.21.1 first. Upgrade versions in `gradle.properties` before attempting a 1.21.x or 26.x port.
