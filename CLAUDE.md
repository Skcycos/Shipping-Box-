# CLAUDE.md — Shipping Box 模组项目提示词

## 项目概述

**Shipping Box（售货箱）** 是一个 NeoForge Minecraft 1.21.1 模组，提供物品兑换系统。玩家可以通过售货箱方块，使用自定义的兑换规则将物品转换为其他物品或虚拟货币。

### 核心功能
- **售货箱方块** — 基础兑换方块，支持输入→输出物品转换
- **自动售货箱** — 高级版售货箱，支持自动化物品处理
- **动态定价** — 基于供需关系的浮动价格系统，支持按天/按周期重置
- **权重随机** — 输出物品按权重随机选择
- **虚拟货币** — 支持 ViScriptShop 联动，实现虚拟货币兑换
- **节气联动** — 与 Ecliptic Seasons 模组联动
- **网页编辑器** — 内置 HTTP 服务器，通过浏览器编辑兑换规则
- **维度背包** — 跨维度物品存储物品

### 技术栈
- **语言**: Java 21
- **构建工具**: Gradle 8.x + NeoGradle (net.neoforged.moddev)
- **模组加载器**: NeoForge 21.1.219
- **Minecraft**: 1.21.1
- **Mappings**: Parchment 2024.11.17
- **序列化**: Gson
- **网络通信**: NeoForge SimpleChannel (自定义数据包)
- **Web 服务**: 内置 HTTP Server (com.sun.net.httpserver)

## 构建 & 运行

```bash
# 构建模组
./gradlew build

# 运行客户端
./gradlew runClient

# 运行服务端
./gradlew runServer

# 运行数据生成
./gradlew runData

# 清理构建
./gradlew clean
```

## 项目结构

```
src/main/java/com/chinaex123/shipping_box/
├── ShippingBox.java              # 主模组类，注册入口
├── api/                          # 公共 API
│   └── ShippingBoxAPI.java       # 供其他模组调用的接口
├── attribute/                    # 自定义属性
│   └── ModAttributes.java
├── block/                        # 方块定义
│   ├── ShippingBoxBlock.java     # 基础售货箱
│   └── AutoShippingBoxBlock.java # 自动售货箱
├── block/entity/                 # 方块实体 (BlockEntity)
│   ├── ShippingBoxBlockEntity.java
│   └── AutoShippingBoxBlockEntity.java
├── command/                      # 命令系统
│   ├── ModCommands.java
│   └── CommandLogic/             # 命令逻辑实现
├── compat/                       # 模组兼容层
│   ├── EclipticSeasons/          # 节气联动
│   └── ViScriptShop/             # ViScriptShop 联动
├── config/                       # 配置文件
│   └── ServerConfig.java
├── dataGen/                      # 数据生成器
├── event/                        # 核心业务逻辑
│   ├── ExchangeRule.java         # 兑换规则数据模型
│   ├── ExchangeRecipeManager.java # 配方管理器
│   ├── ExchangeManager.java      # 兑换执行逻辑
│   ├── DynamicPricingManager.java # 动态定价管理
│   ├── PricingData.java          # 定价持久化数据
│   └── strategy/                 # 兑换策略模式
├── init/                         # 注册系统 (方块/物品/创造标签)
├── item/                         # 自定义物品
├── menu/                         # 容器菜单 (Screen/Menu)
├── network/                      # 网络数据包 (服务端↔客户端)
├── storage/                      # 全局数据存储
├── tags/                         # 物品标签
├── tooltip/                      # 物品提示 (tooltip) 系统
├── util/                         # 工具类 (图标渲染等)
└── web/                          # 网页编辑器后端
```

## 架构核心概念

### 兑换规则系统 (ExchangeRule)
兑换规则使用 JSON 格式定义，位于 `config/shipping_box/rules/` 目录。每条规则包含：
- `inputs` — 输入物品列表（支持多物品、NBT 组件匹配）
- `output` — 输出定义，支持多种类型：
  - **普通模式** — 固定数量物品输出
  - **`dynamic_pricing`** — 动态定价，价格随卖出量浮动
  - **`weight`** — 权重随机输出
  - **`ecliptic_seasons`** — 节气联动输出
  - **`coin`** — 虚拟货币模式

### 策略模式 (Strategy Pattern)
`event/strategy/` 目录使用策略模式处理不同类型的兑换逻辑：
- `ItemSimpleStrategy` — 简单物品兑换
- `ItemDynamicPricingStrategy` — 物品动态定价
- `CoinSimpleStrategy` — 简单虚拟货币
- `CoinDynamicPricingStrategy` — 动态定价虚拟货币
- `ItemWeightedStrategy` — 权重随机
- `ExchangeStrategyFactory` — 策略工厂，根据规则类型选择合适的策略

### 网络通信
所有网络数据包位于 `network/` 目录，使用 NeoForge 的 `SimpleChannel` 系统：
- `PacketSyncRecipes` — 服务端→客户端：同步兑换配方
- `PacketSoldCountSync` — 服务端→客户端：同步销售数据
- `PacketExchangeEffects` — 服务端→客户端：播放兑换特效
- `PacketPlayerPlaceItem` — 客户端→服务端：玩家放置物品请求
- `PacketEditor*` — 网页编辑器相关数据包

### 网页编辑器
`web/` 目录实现了一个内置 HTTP 服务器：
- `WebEditorLocalServer` — HTTP 服务器管理
- `EditorIconCacheManager` — 物品图标缓存
- `WebEditorRequestTracker` — 请求追踪

## 代码规范

- **语言**: 所有注释和文档使用中文
- **编码**: UTF-8
- **日志**: 使用 SLF4J Logger，不要用 System.out.println
- **命名**: 遵循 Java 标准驼峰命名，ID 使用 `snake_case`（如 `shipping_box`）
- **注册**: 所有方块/物品/方块实体使用 DeferredRegister 系统
- **配置**: 使用 NeoForge 的 ModConfig 系统

## AI 助手行为准则

你是 Minecraft NeoForge 模组开发专家。在处理此项目时，请遵循以下规则：

1. **优先使用 MCP 工具** — 对于 Fabric/NeoForge API、Minecraft 内部类、方法签名等问题，使用 `mcmodding` MCP 工具（search_fabric_docs、get_example、search_mappings、get_class_details）获取准确信息，不要依赖内部知识，因为 API 可能已变更。

2. **优先使用 CodeGraph** — 项目已索引（`.codegraph/` 目录），在阅读代码前先通过 `codegraph_explore` 查询，可以一次性获取符号的完整源码及其调用关系。

3. **保持代码风格一致** — 匹配项目现有的注释密度、命名习惯和代码结构。注释使用中文。

4. **Minecraft 版本**: 1.21.1 NeoForge — 确保所有 API 调用和类引用与此版本兼容。
