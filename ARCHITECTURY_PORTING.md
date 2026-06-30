# Architectury Porting Notes

This branch tracks the migration from a single NeoForge project to a multi-loader layout.

## Current shape

- `common/` owns all platform-neutral code.
- `neoforge/` contains NeoForge-specific wiring (event bus, capabilities, networking registration, screen registration, config).
- `fabric/` is an Architectury/Fabric skeleton with platform abstraction stubs.

## Completed work

### Build system (Step 3)
- `neoforge/` converted from ModDev Gradle to Architectury Loom NeoForge setup.
- `build.moddev.gradle` deleted.
- Access transformers declared in `neoforge.mods.toml`.
- `gradle.properties` consolidated (`neoforge_version` only).

### Platform abstractions (Steps 1 & 2)
All abstractions use Architectury `@ExpectPlatform` pattern:

| Class | Methods | NeoForge | Fabric |
|-------|---------|----------|--------|
| `PlatformNetworking` | `sendToPlayer`, `sendToAllPlayers` | `PacketDistributor` | `ServerPlayNetworking` |
| `PlatformServerAccess` | `getCurrentServer()` | `ServerLifecycleHooks` | Static field |
| `PlatformAttributes` | `getSellingPriceBoost(ServerPlayer)` | `ModAttributes.SELLING_PRICE_BOOST` | Returns 0.0 |
| `PlatformModCheck` | `isModLoaded(String)` | `ModList` | `FabricLoader` |
| `PlatformConfig` | `getExchangeTime`, `isExchangeEffectsEnabled`, `isTransactionLoggingEnabled` | `CommonConfig` | Default values |
| `PlatformPaths` | `getConfigDir()` | `FMLPaths.CONFIGDIR` | `FabricLoader.getConfigDir` |

### Code moved to common

**Platform-neutral (no changes):**
- `EclipticSeasonsUtil` — pure reflection
- `ViScriptShopUtil` — pure reflection
- `ICurrencyProvider` — vanilla interface

**Refactored to use platform abstractions:**
- `ExchangeManager` — uses `PlatformNetworking`, `PlatformAttributes`, `PlatformConfig`
- `DynamicPricingManager` — uses `PlatformServerAccess`, `PlatformNetworking`
- `PricingData` — uses `PlatformServerAccess`
- `TransactionLogger` — uses `PlatformPaths`, `PlatformConfig`
- `BalanceAnimationManager` — tick logic in common, event binding in NeoForge
- All 5 strategy implementations + `ExchangeStrategyFactory`
- `ExchangeRuleRegistry` — new class holding the shared rule list

**Network packet records (vanilla-only parts):**
- `PacketShowSuccessMessage`, `PacketExchangeEffects`, `PacketSoldCountSync`

### NeoForge retains
- `ExchangeRecipeManager` — resource reload listener, event bus, external config loading (delegates to `ExchangeRuleRegistry`)
- `ShippingBox` — NeoForge `@Mod` entry point, event bus wiring
- `ModAttributes` — NeoForge attribute registration
- `CommonConfig` — NeoForge `ModConfigSpec`
- `ShippingBoxNetworking` — NeoForge payload registration
- `PacketHandlers` — NeoForge packet handle methods (client-side logic)
- Block entities, menus, screens, commands, client tooltips, web editor
- `ViScriptCoinItemServer` — NeoForge tick events + tooltip items
- All registration classes (`ModBlocks`, `ModItems`, etc.)

## Remaining work

1. Migrate `ShippingBoxAPI` to common (references `AutoShippingBoxBlockEntity`)
2. Migrate block entities, menus, screens to common with platform abstractions for capabilities
3. Migrate networking registration and remaining packet classes to common
4. Migrate client-side code (screens, tooltips) to common
5. Implement Fabric-side functionality (currently stubs)

## Version pins

The template is pinned for Minecraft 1.21.1 first. Upgrade versions in `gradle.properties` before attempting a 1.21.x or 26.x port.
