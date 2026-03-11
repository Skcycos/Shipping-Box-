# 📦 售货箱（Shipping Box）功能分类文档

[English](#english) | [中文](#中文)

---

# **English**

### Adding a Shipping Box for Item-to-Item Exchange

## I. Core Mechanics
- **Exchange Logic**
  - Define "exchange rules" through data packs
  - After placing items in the shipping box, exchange will occur at 6:00 the next day according to the rules set in the "exchange rules"

- **Output Dynamic Threshold Mode**
  - **Type**: `dynamic_pricing`
  - **Threshold array** `threshold`: Defines sales volume critical points for price changes (e.g., [64, 128, 256, 512])
  - **Value array** `value`: Corresponding output quantities after each threshold (e.g., [4, 3, 2, 1])
  - **Reset time** `day`: Number of days until threshold reset
    - `day = -1`: Sales count never resets, accumulates indefinitely
    - `day = 0`: Sales count automatically resets to 0 daily
    - `day = N` (N > 0): Sales count resets every N days
  - **Correspondence**: Threshold array and value array must correspond one-to-one
  - **Price Calculation Rules**:
    - Sales volume < minimum threshold → Use first tier price
    - Sales volume ≥ maximum threshold → Use last tier price
    - Sales volume between thresholds → Use corresponding tier price
  - **Statistics Scope**: Sales statistics shared among all players
  - Can be configured per item in JSON whether to enable
  - **Configuration Example**:
    ```json
    {
      "input": {
        "item": "minecraft:stone",
        "count": 1
      },
      "output": {
        "type": "dynamic_pricing",
        "item": "minecraft:diamond",
        "dynamic_properties": {
          "threshold": [64, 128, 256, 512],
          "value": [4, 3, 2, 1],
          "day": 3
        }
      }
    }
    ```

- **Output Weight Mode**
  - **Type**: `weight`
  - In this mode, item output randomly obtains an item based on weight
  - **Configuration Example**:
    ```json
    {
      "input": {
        "item": "minecraft:nether_star",
        "count": 1
      },
      "output": {
        "type": "weight",
        "items": [
          {"item": "minecraft:diamond", "count": 1, "weight": 1},
          {"item": "minecraft:emerald", "count": 2, "weight": 2},
          {"item": "minecraft:iron_ingot", "count": 5, "weight": 3},
          {"item": "minecraft:redstone", "count": 5, "weight": 3}
        ]
      }
    }
    ```

## II. User Interface & Interaction
- **JEI Integration**
  - Items automatically have exchange information, supporting JEI list display of item exchange information
- **Configuration Error Alerts**
  - In-game alerts when "exchange rules" are configured incorrectly

## III. Configuration Method
- **Rule File Path**
  - "Exchange rules" must be placed in the `data/shipping_box/exchange_rules/` folder
  - **File Format**
  - Files must be JSON, multiple JSON files are supported
  - **Rule Structure**: Use `"rules"` array containing multiple exchange rules
    ```json
      {
        "rules": [
          {
            "input": {
              "item": "minecraft:stone",
              "count": 1
            },
            "output": {
              "item": "minecraft:diamond",
              "count": 1
            }
          }
        ]
      }
      ```
### Item Attribute System

#### Selling Price Boost Attribute
- **Attribute Name**: `selling_price:selling_price_boost`
- **Default Value**: `0.0`
- **Maximum Value**: `10.0`
- **Function Description**: This attribute affects item selling price as a percentage, higher values yield more when selling
  - For example: `selling_price_boost = 0.5` means a 50% price increase
  - `selling_price_boost = 2.0` means a 200% price increase
- **Application Scope**: Applies to all exchangeable items, including:
  - Item-to-item exchange mode
  - Virtual currency exchange mode
- **Configuration Method**: Currently unobtainable in normal game modes

### Input Configuration Types
| Type               | Description                         | Example                                                                                  |
|--------------------|-------------------------------------|------------------------------------------------------------------------------------------|
| **Single Item**    | Single item as input                | `{"item": "minecraft:stone", "count": 1}`                                                |
| **Multiple Items** | Multiple items combination as input | `[{"item": "minecraft:emerald", "count": 1}, {"item": "minecraft:diamond", "count": 2}]` |
| **Tag**            | Use item tag as input               | `{"tag": "#minecraft:logs", "count": 1}`                                                 |

### Output Configuration Types
| Type                  | Description                          | Example                                                                   |
|-----------------------|--------------------------------------|---------------------------------------------------------------------------|
| **Single Item**       | Output single item                   | `{"item": "shipping_box:copper_creeper_coin", "count": 1}`                |
| **Weight Mode**       | Random output based on weight        | `{"type": "weight", "items": [...]}`                                      |
| **Dynamic Threshold** | Sales volume affects output quantity | `{"type": "dynamic_pricing", "item": "...", "dynamic_properties": {...}}` |

### Component System
- **Supports input/output data components**
- **Component Formats**:
  - **String Format**: `"components": "damage=100"`
  - **JSON Object Format (Recommended)**: `"components": {...}`

### Standard Component Examples
| Type               | String Format                                                          | JSON Object Format                                                                           |
|--------------------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **Potion**         | `"{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"`      | `{"potion_contents": {"potion": "minecraft:night_vision"}}`                                  |
| **Enchanted Item** | `"{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"`       | `{"enchantments": {"levels": {"minecraft:sharpness": 3, "minecraft:unbreaking": 3}}}`        |
| **Enchanted Book** | `"{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"` | `{"stored_enchantments": {"levels": {"minecraft:sharpness": 5, "minecraft:unbreaking": 3}}}` |

### Interval Format Description
Used for numerical range matching (such as durability, fish length, etc.):

| Format      | Description             | Example       |
|-------------|-------------------------|---------------|
| `[min,max]` | Inclusive boundaries    | `[40.0,50.0]` |
| `(min,max)` | Exclusive boundaries    | `(40.0,50.0)` |
| `(min,max]` | Left-open, right-closed | `(40.0,50.0]` |
| `[min,max)` | Left-closed, right-open | `[40.0,50.0)` |

### Other Mod Component Examples
[MOD] Quality Food - Crop quality
- Support input and output
```json
{
  "components": {
    "quality_food:quality": {
      "level": 3,
      "type": "quality_food:diamond"
    }
  }
}
```

[MOD] Tide - The weight and length of the fish
- Support input and output
```json
{
  "components": {
    "tide:fish_length": "[40.0,50.0)",
    "tide:catch_timestamp": "[5000,6000]"
  }
}
```

[MOD]Kaleidoscope Tavern - Brewing quality
- Support input and output
```json
{
  "components": {
    "kaleidoscope_tavern:brew_level": 7
  }
}
```

## IV. Integrated Mod

## **ViScriptShop**

### 1. Crawler Coin
- **Right-click**: Exchange for virtual currency based on the currency price displayed in the item tooltip
- **Sneak + Right-click**: Exchange one full stack

### 2. Secondary Coin Pouch
- **Right-click**: Convert physical currency or check balance
- **Sneak + Right-click**: Exchange physical currency from containers

### 3. Exchange Rule Extensions

#### Virtual Currency Exchange Mode
- **Identifier**: Replace `item` in output with `"coin": true`
- **Function**: Directly exchange input items for the mod's virtual currency
- **Amount**: `count` field specifies the amount of virtual currency to exchange
- **Basic Example**:
  ```json
  {
    "input": {
      "item": "minecraft:stone",
      "count": 1
    },
    "output": {
      "coin": true,
      "count": 10
    }
  }
  ```

#### Dynamic Threshold + Virtual Currency Exchange Mode
- **Function**: Sales volume affects the amount of virtual currency exchanged
- **Configuration**: `value` array specifies virtual currency amounts for corresponding price tiers
- **Example**:
  ```json
  {
    "input": {
      "item": "minecraft:cobblestone",
      "count": 1
    },
    "output": {
      "type": "dynamic_pricing",
      "coin": true,
      "dynamic_properties": {
        "threshold": [64, 128, 256, 512],
        "value": [10, 5, 3, 1],
        "day": 5
      }
    }
  }
  ```

## Ecliptic Seasons

### Seasonal Influence System
- **Function Description**: Determines whether the sold item is in the current season and adjusts the selling price based on seasonal status
- **Compatibility Note**: This mode is **NOT compatible** with ViScriptShop's virtual currency (VSS) mode
- **Core Mechanics**:
  - In-season items: Receive selling price bonus
  - Off-season items: Receive selling price reduction
  - Configurable option to allow selling only in specific seasons

### Seasonal Configuration Parameters
| Parameter             | Type    | Description                                                                                                 |
|-----------------------|---------|-------------------------------------------------------------------------------------------------------------|
| `season`              | Array   | Sets the seasonal属性 of the item. Available values: `spring`, `summer`, `autumn`, `winter`, `all`            |
| `seasonal_only`       | Boolean | `true`: Can only be sold during set seasons; `false`: Available year-round, but price is affected by season |
| `add_season_bonus`    | Integer | Price bonus percentage when in-season (e.g., 30 means 30% price increase)                                   |
| `reduce_season_bonus` | Integer | Price reduction percentage when off-season (e.g., 20 means 20% price decrease)                              |

### Configuration Example
```json
{
  "input": {
    "item": "minecraft:carrot",
    "count": 1
  },
  "output": {
    "type": "ecliptic_seasons",
    "item": "minecraft:emerald",
    "ecliptic_seasons": {
      "season": ["winter"],
      "seasonal_only": false,
      "add_season_bonus": 30,
      "reduce_season_bonus": 20
    }
  }
}
```

### Seasonal Configuration Notes
- **season field**: Must use array format, multiple seasons can be set simultaneously
  - Example: `["spring", "autumn"]` represents spring and autumn
- **seasonal_only function**:
  - `true`: Items can only be sold during set seasons, cannot be exchanged in other seasons
  - `false`: Items can be sold year-round, but prices fluctuate based on season
- **Price Calculation Formula**:
  - In-season: Final price = Base price × (1 + add_season_bonus/100)
  - Off-season: Final price = Base price × (1 - reduce_season_bonus/100)

---

# **中文**

### 添加一个用于物品兑换物品的售货箱

## 一、核心机制
- **兑换逻辑**
  - 通过数据包定义"兑换规则"
  - 将物品放入售货箱后，在第二天6:00会按照"兑换规则"内设置的规则进行兑换

- **输出动态阈值模式**
  - **类型**：`dynamic_pricing`（动态定价）
  - **阈值数组** `threshold`：定义价格变更的销量临界点（如 [64, 128, 256, 512]）
  - **价值数组** `value`：对应每个阈值后的输出数量（如 [4, 3, 2, 1]）
  - **重置时间** `day`：阈值重置所需天数
    - `day = -1`：销售计数永不重置，会一直累加
    - `day = 0`：每天自动重置销售计数为0
    - `day = N`（N > 0）：每N天重置一次销售计数
  - **对应关系**：阈值数组和价值数组必须一一对应
  - **价格计算规则**：
    - 销量 < 最小阈值 → 使用第一档价格
    - 销量 ≥ 最大阈值 → 使用最后一档价格
    - 销量介于阈值之间 → 使用对应档位价格
  - **统计范围**：所有玩家共享销售统计
  - 可在JSON中为每个物品配置是否启用
  - **配置示例**：
    ```json
    {
      "input": {
        "item": "minecraft:stone",
        "count": 1
      },
      "output": {
        "type": "dynamic_pricing",
        "item": "minecraft:diamond",
        "dynamic_properties": {
          "threshold": [64, 128, 256, 512],
          "value": [4, 3, 2, 1],
          "day": 3
        }
      }
    }
    ```

- **输出权重模式**
  - **类型**：`weight`（权重模式）
  - 在这个模式下，物品输出会根据权重来随机获得一个物品
  - **配置示例**：
    ```json
    {
      "input": {
        "item": "minecraft:nether_star",
        "count": 1
      },
      "output": {
        "type": "weight",
        "items": [
          {"item": "minecraft:diamond", "count": 1, "weight": 1},
          {"item": "minecraft:emerald", "count": 2, "weight": 2},
          {"item": "minecraft:iron_ingot", "count": 5, "weight": 3},
          {"item": "minecraft:redstone", "count": 5, "weight": 3}
        ]
      }
    }
    ```

## 二、用户界面与交互
- **JEI集成**
  - 物品自动有兑换信息，支持jei列表显示物品的兑换信息
- **配置错误提醒**
  - "兑换规则"配置错误时会在游戏内提醒

## 三、配置方式
- **规则文件路径**
  - 需要将“兑换规则”放入到`data/shipping_box/exchange_rules/`文件夹内
  - **文件格式**
  - 文件必须是json，可以有多个json
  - **规则结构**：使用`"rules"`数组包含多条兑换规则
    ```json
      {
        "rules": [
          {
            "input": {
              "item": "minecraft:stone",
              "count": 1
            },
            "output": {
              "item": "minecraft:diamond",
              "count": 1
            }
          }
        ]
      }
      ```
### 物品属性系统

#### 售价加成属性
- **属性名称**：`selling_price:selling_price_boost`
- **默认值**：`0.0`
- **最大值**：`10.0`
- **功能说明**：该属性以百分比形式影响物品售价，数值越高，出售所得越多
  - 例如：`selling_price_boost = 0.5` 表示售价提升 50%
  - `selling_price_boost = 2.0` 表示售价提升 200%
- **应用范围**：适用于所有可兑换物品，包括：
  - 物品兑换物品模式
  - 虚拟货币兑换模式
- **配置方式**：暂时无法在常规模式下获得

### 输入配置类型
| 类型      | 说明         | 示例                                                                                       |
|---------|------------|------------------------------------------------------------------------------------------|
| **单物品** | 单个物品作为输入   | `{"item": "minecraft:stone", "count": 1}`                                                |
| **多物品** | 多个物品组合作为输入 | `[{"item": "minecraft:emerald", "count": 1}, {"item": "minecraft:diamond", "count": 2}]` |
| **标签**  | 使用物品标签作为输入 | `{"tag": "#minecraft:logs", "count": 1}`                                                 |

### 输出配置类型
| 类型       | 说明       | 示例                                                                        |
|----------|----------|---------------------------------------------------------------------------|
| **单物品**  | 输出单个物品   | `{"item": "shipping_box:copper_creeper_coin", "count": 1}`                |
| **权重模式** | 按权重随机输出  | `{"type": "weight", "items": [...]}`                                      |
| **动态阈值** | 销量影响输出数量 | `{"type": "dynamic_pricing", "item": "...", "dynamic_properties": {...}}` |

### 组件系统
- **支持输入/输出数据组件**
- **组件格式**：
  - **字符串格式**：`"components": "damage=100"`
  - **JSON对象格式（推荐）**：`"components": {...}`

### 标准组件示例
| 类型       | 字符串格式                                                                  | JSON对象格式                                                                                     |
|----------|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| **药水**   | `"{\"potion_contents\":{\"potion\":\"minecraft:night_vision\"}}"`      | `{"potion_contents": {"potion": "minecraft:night_vision"}}`                                  |
| **附魔物品** | `"{\"enchantments\":{\"levels\":{\"minecraft:unbreaking\":1}}}"`       | `{"enchantments": {"levels": {"minecraft:sharpness": 3, "minecraft:unbreaking": 3}}}`        |
| **附魔书**  | `"{\"stored_enchantments\":{\"levels\":{\"minecraft:sharpness\":5}}}"` | `{"stored_enchantments": {"levels": {"minecraft:sharpness": 5, "minecraft:unbreaking": 3}}}` |

### 区间格式说明
用于数值范围匹配（如耐久度、鱼长度等）：

| 格式            | 说明   | 示例          |
|:--------------|:-----|:------------|
| `[40.0,50.0]` | 包含边界 | [40.0,50.0] |
| `(40.0,50.0)` | 排除边界 | (40.0,50.0) |
| `(40.0,50.0]` | 左开右闭 | (40.0,50.0] |
| `[40.0,50.0)` | 左闭右开 | [40.0,50.0) |

### 其他模组组件示例
[MOD]Quality Food - 作物的品质
- 支持输入输出
```json
{
  "components": {
    "quality_food:quality": {
      "level": 3,
      "type": "quality_food:diamond"
    }
  }
}
```

[MOD]潮汐 - 鱼的重量和长度
- 支持输入输出
```json
{
  "components": {
    "tide:fish_length": "[40.0,50.0)",
    "tide:catch_timestamp": "[5000,6000]"
  }
}
```

[MOD]森罗物语：酒馆 - 酿酒品质
- 支持输入输出
```json
{
  "components": {
    "kaleidoscope_tavern:brew_level": 7
  }
}
```

## 四、联动模组

## **ViScriptShop**

### 1. 爬爬币
- **右键**：根据物品提示显示的货币价格兑换虚拟货币
- **潜行右键**：换取一组

### 2. 次元钱袋
- **右键**：转换实体货币或查询余额
- **潜行右键**：兑换容器内的实体货币

### 3. 兑换规则扩展

#### 虚拟货币兑换模式
- **标识**：在output中将`item`替换为`"coin": true`
- **功能**：直接将输入物品兑换为模组的虚拟货币
- **金额**：`count`字段指定兑换的虚拟货币数量
- **基础示例**：
  ```json
  {
    "input": {
      "item": "minecraft:stone",
      "count": 1
    },
    "output": {
      "coin": true,
      "count": 10
    }
  }
  ```

#### 动态阈值 + 虚拟货币兑换模式
- **功能**：销量影响虚拟货币的兑换数量
- **配置**：`value`数组指定对应价格区间的虚拟货币数量
- **示例**：
  ```json
  {
    "input": {
      "item": "minecraft:cobblestone",
      "count": 1
    },
    "output": {
      "type": "dynamic_pricing",
      "coin": true,
      "dynamic_properties": {
        "threshold": [64, 128, 256, 512],
        "value": [10, 5, 3, 1],
        "day": 5
      }
    }
  }
  ```

## 节气联动

### 季节影响系统
- **功能说明**：判断售卖的物品是否在当前季节，根据季节状态调整售价
- **兼容性说明**：此模式**不兼容**ViScriptShop的虚拟货币（VSS）模式
- **核心机制**：
  - 应季物品：获得售价加成
  - 非应季物品：受到售价减益
  - 可配置是否仅允许在特定季节出售

### 季节配置参数
| 参数                    | 类型  | 说明                                                                        |
|-----------------------|-----|---------------------------------------------------------------------------|
| `season`              | 数组  | 设定物品的季节属性，可选值：`spring`(春)、`summer`(夏)、`autumn`(秋)、`winter`(冬)、`all`(所有季节) |
| `seasonal_only`       | 布尔值 | `true`：仅限设定的季节才能出售；`false`：全年可售，但价格受季节影响                                  |
| `add_season_bonus`    | 整数  | 应季时的价格加成百分比（如30表示售价提升30%）                                                 |
| `reduce_season_bonus` | 整数  | 非应季时的价格减益百分比（如20表示售价降低20%）                                                |

### 配置示例
```json
{
  "input": {
    "item": "minecraft:carrot",
    "count": 1
  },
  "output": {
    "type": "ecliptic_seasons",
    "item": "minecraft:emerald",
    "ecliptic_seasons": {
      "season": ["winter"],
      "seasonal_only": false,
      "add_season_bonus": 30,
      "reduce_season_bonus": 20
    }
  }
}
```

### 季节配置说明
- **season字段**：必须使用数组格式，可同时设置多个季节
  - 例如：`["spring", "autumn"]` 表示春季和秋季
- **seasonal_only功能**：
  - `true`：物品只在设定的季节可以出售，其他季节无法兑换
  - `false`：物品全年可出售，但价格会根据季节浮动
- **价格计算公式**：
  - 应季时：最终售价 = 基础价格 × (1 + add_season_bonus/100)
  - 非应季时：最终售价 = 基础价格 × (1 - reduce_season_bonus/100)

---

## License | 许可证

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

本项目采用MIT许可证 - 详情请参阅 [LICENSE](LICENSE) 文件。