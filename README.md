# ArchWeaver

<!-- [English](./docs/all_readme/README_EN.md) | 简体中文 -->

适用于 Minecraft 26.1.2 / NeoForge 的的辅助模组，目前提供假玩家管理与区块加载功能，支持假人生成、背包管理、移动、交互及预设。

<!-- ![icon](./common/src/main/resources/xxx.png) -->

## 开发原因

Neoforge 高版本没有类似 Carpet 的假人 mod，还有做生电机器时，始终区块加载很麻烦，因此就有了这个模组。之后打算顺下去扩展，比如让假人接入AI，添加类似建筑之杖等世界编辑功能，
因此模组名使用 ArchWeaver（上层编织者？/架构编织者？）。主要是听起来很有逼格，而且可以联想到 Architect（建筑师）。

## 简介

- 默认按 `C` 打开假人面板，包括区块加载地图、假人列表、预设和分组管理等
- 默认按 `F3` 显示各种区块加载信息
- 右键假玩家，打开对应控制界面

<!-- ## 图例

![1](./docs/image/1.png)
![2](./docs/image/2.png)
![3](./docs/image/3.png) -->

## 使用

### 假人功能

1. 模拟加载：玩家模式/玩偶模式
2. 视角、朝向、移动、飞行、主手位置、持续动作控制、丢弃、骑乘等
3. 假人信息，包括游戏模式，名称，坐标等
4. 假人背包、盔甲、副手、末影箱等
5. 假人附身功能（只改变物品栏和视角等，像是成就等不改变，因此不用“夺舍”）
6. 自动行为：（注：这些都是服务端功能，因此和Tweakeroo等客户端模组不同，也仅用于假人）
   | 设置 | 说明 |
   | ---------- |----------------------------------------------------------- |
   | 自动补货 | 手中可堆叠物品剩余不超过一组的 1/8 时，从 36 格主背包补到半组。 |
   | 潜影盒补货 | 补货时也搜索主背包内的潜影盒；需同时开启普通补货。 |
   | 自动换工具 | 主手或副手工具剩余耐久不超过 10 时，换上背包中剩余耐久最高的同种物品。 |
   | 自动钓鱼 | 原版浮漂咬钩后约 5 刻自动收杆，再等待 10 刻抛竿。 |

模拟加载默认关闭；启用后以假人为中心保持方形区块模拟范围，距离不能超过服务端 `simulation-distance`，所有在线假人的范围按“维度 + 区块坐标”去重后计算；总量还受 `maxPlayerLoadingChunks` 预算限制，设为 `-1` 表示不限。

飞行状态由假人物品栏右侧的操控区控制：竖排中间按钮平时为 `J`（跳跃），飞行时切换为 `F`（关闭飞行）；上、下按钮单击一次升降一次，长按则持续升降，松开停止。旁观模式必须保持飞行，其关闭飞行按钮会被禁用，也无法通过命令关闭。

### 区块加载功能

1. 支持在地图上进行选中区块进行强加载。

## 构建

```powershell
.\gradlew.bat build
```

生成的 JAR 会自动复制到根目录的 `build` 中并重命名为：

- `build/v<模组版本>/archweaver-v<模组版本>-mc<MC版本>-<加载器类型>.jar`

运行开发客户端分别使用：

```powershell
.\gradlew.bat :neoforge:runClient
```

在 Windows 上，也可以运行 `tools\1.一键启动mc脚本.ps1`。

## 详细说明

### 命令

命令中的 `<名称>` 表示必填参数，`[选项]` 表示可选参数。命令格式采用“命令 → 语法 → 分类”的列表形式。

#### `/fakeplayer` 命令

`/fakeplayer`：统一的假玩家控制命令。

##### 生成与清除

- `/fakeplayer`：在执行位置以自动名称生成假玩家。
- `/fakeplayer <名称>`：在执行位置生成指定名称的假玩家。
- `/fakeplayer spawn <名称>`：与上一条命令相同。
- `/fakeplayer kill <名称>`：保存并移除假玩家，不触发死亡掉落。

##### 附身与界面

- `/fakeplayer possess <名称>`：附身指定假玩家。
- `/fakeplayer unpossess`：退出当前附身。
- `/fakeplayer list`：列出当前所有假玩家。
- `/fakeplayer gui [名称]`：打开地图设置，指定名称时打开该假玩家的物品栏管理页面。
- `/fakeplayer setting [名称]`：`gui` 的别名。
- `/fakeplayer player <名称> <操作>`：等价于 `/player <名称> <操作>`，操作列表见下一节。

生成位置、维度和朝向取自命令执行来源。游戏模式继承执行命令的玩家，控制台执行时默认为创造模式。

#### 预设和分组命令

直接执行 `/fakeplayer preset` 会打开预设管理页，`/fakeplayer group` 会打开分组管理页；也可从地图底栏进入。预设和分组分别通过 `preset` 和 `group` 子命令管理。

预设保存名称、UUID、原版玩家数据快照（包括维度、位置、朝向、背包、经验、游戏模式和能力等）、持续动作和可选描述。预设本身不会上线，只有执行
`load` 后才会生成假玩家。

- `/fakeplayer preset list [页码]`：分页列出预设，并提供加载和删除快捷按钮。
- `/fakeplayer preset save <预设> <在线假人> [描述]`：保存当前假人状态，新建或覆盖预设。
- `/fakeplayer preset load <预设>`：加载单个预设。
- `/fakeplayer preset remove <预设>`：删除预设，并从所有分组移除该成员。

- `/fakeplayer group create <组>`：创建空分组。
- `/fakeplayer group list [页码]`：分页列出分组及成员数，并提供快捷操作。
- `/fakeplayer group add <组> <预设>`：将预设加入分组。
- `/fakeplayer group remove <组> <预设>`：将单个预设移出分组。
- `/fakeplayer group load <组>`：批量加载，分别统计成功和失败数。
- `/fakeplayer group unload <组>`：批量移除由组内预设对应的在线假人。
- `/fakeplayer group info <组> [页码]`：分页查看分组成员，并可加载或移出成员。
- `/fakeplayer group remove <组>`：删除分组，不删除其中的预设。

驻留清单只在世界 `data/archweaver/fake_players.dat` 中保存假人的身份（UUID 和名称），不保存持续动作；预设在同一文件中保存创建时的完整原版玩家数据快照。
在线驻留假人的背包、位置、能力、经验等实时状态由原版 `playerdata/<UUID>.dat` 负责保存和恢复。
正常移除或死亡时，假人从驻留清单删除；服务器正常退出时会保留驻留记录，以便下次启动恢复。真玩家登录接管身份时同样保留驻留记录，服务器下次启动时仅在名称和 UUID 均未被占用时恢复该假人，持续动作每次启动后由操作者重新设置。每次启动读取前还会将现有存档复制为
带时间戳的 `fake_players.*.dat.bak`，避免解析失败后的空存档覆盖唯一的排查副本。

#### `/player` 命令

所有 `/player` 命令均使用 `/player <名称> <操作>` 的顺序，写成 `/fakeplayer player <名称> <操作>` 完全等价。

##### 生成与清除

- `/player <名称> spawn`：在命令执行位置生成指定名称的假玩家，也可指定位置、朝向、维度和游戏模式。
- `/player <名称> kill`：保存并移除假玩家，不触发死亡掉落。
- `/player <名称> shadow`：踢出同名在线真玩家，在其位置生成继承状态的同名假玩家；非管理员只能替换自己。

`spawn` 支持以下语法：

```text
/player <名称> spawn
/player <名称> spawn gamemode <游戏模式>
/player <名称> spawn at <位置>
/player <名称> spawn at <位置> facing <旋转>
/player <名称> spawn at <位置> facing <旋转> in <维度>
/player <名称> spawn at <位置> facing <旋转> in <维度> gamemode <游戏模式>
# 游戏模式部分，carpet 使用的是 in 而不是 gamemode ，这里暂时没有兼容它的写法，不要搞错了。
```

例如：

```text
/player Steve spawn at ~ ~1 ~ facing 0 90 in minecraft:the_nether gamemode creative
```

> 省略参数时继承并使用命令源（玩家/命令方块）的信息。但是原有且未指定就会继承原有的游戏模式。

##### 界面与背包

- `/player <名称> gui` / `/player <名称> gui bag`：打开完整物品栏：36 格主背包、4 格盔甲和 1 格副手。
- `/player <名称> gui enderchest`：打开假玩家的末影箱。
- `/player <名称> setting`：打开假玩家物品栏管理页面。
- `/player <名称> setting default` / `/player <名称> setting reset`：将动作和输入设置恢复为默认值，不修改背包等数据。
- `/player <名称> possess`：附身该假玩家。
- `/player <名称> unpossess`：当正在附身该假玩家时退出附身。

自动化设置命令如下，省略 `on/off/toggle` 时默认为切换：

- `/player <名称> automation <autoReplenishment|shulkerReplenishment|autoReplaceTools|autoFishing> [on|off|toggle]`：切换自动补货、潜影盒补货、自动换工具或自动钓鱼；省略 `on/off/toggle` 时默认为切换。

##### 物品操作

- `/player <名称> drop [选项]`：丢出一个物品，默认使用主手。
- `/player <名称> dropStack [选项]`：丢出整组物品，默认使用主手。
- `/player <名称> hotbar <槽位>`：切换快捷栏，槽位范围为 `1` 至 `9`。
- `/player <名称> swapHands`：交换主手与副手物品。

`drop` 和 `dropStack` 支持以下目标选项：

- `mainhand`：主手物品。
- `offhand`：副手物品。
- `head`、`chest`、`legs`、`feet`：指定护甲槽位。
- `armor`：处理四个护甲槽位。
- `0` 至 `35`：指定背包槽位，其中 `0` 是快捷栏第一格。
- `slot <槽位>`：指定背包槽位，槽位范围为 `0` 至 `35`；也可以直接写槽位数字。
- `all`：处理主背包、护甲栏和副手；`drop` 每个槽位丢出一个，`dropStack` 丢出全部。

目标选项后可添加动作模式：

- `continuous`：每刻持续执行。
- `interval <刻数>`：按指定间隔执行，刻数必须大于 `0`。
- `once`：取消该丢弃动作原有的持续或间隔计划，并执行一次。

例如：

```text
/player robot-1 drop offhand
/player robot-1 dropStack 0
/player robot-1 drop all interval 20
```

##### 骑乘

- `/player <名称> mount`：骑乘附近的载具或可骑乘生物。
- `/player <名称> mount <任意内容>`：强制骑乘最近的任意实体，可通过反复执行尝试驯服生物。
- `/player <名称> dismount`：离开当前骑乘实体。

##### 移动与视角

- `/player <名称> look <方向>`：看向 `north`、`south`、`west`、`east`、`up` 或 `down`。
- `/player <名称> look at <x> <y> <z>`：看向指定坐标。
- `/player <名称> move <方向>`：持续向 `forward`、`backward`、`left` 或 `right` 移动。
- `/player <名称> jump [模式]`：跳跃一次，或按指定模式重复跳跃。
- `/player <名称> fly` / `/player <名称> unfly`：开始或停止飞行。
- `/player <名称> sneak` / `/player <名称> unsneak`：开始或停止潜行。
- `/player <名称> sprint` / `/player <名称> unsprint`：开始或停止疾跑。
- `/player <名称> turn back`：转身 180 度。
- `/player <名称> turn left` / `/player <名称> turn right`：向左或向右旋转 90 度。
- `/player <名称> turn <俯仰角> <水平角>`：设置绝对视角；俯仰角范围为 `-90` 至 `90`。

##### 攻击与使用

- `/player <名称> attack [模式]`：攻击实体或破坏视线内方块，相当于左键。
- `/player <名称> use [模式]`：与实体、方块或手中物品交互，相当于右键。
- `/player <名称> stop`：停止攻击、使用、丢弃、移动和跳跃，并取消潜行与疾跑。

`attack`、`use` 和 `jump` 的模式为 `continuous`、`interval <刻数>` 或 `once`。省略模式时执行一次；`once`
还会取消该动作原有的持续或间隔计划。模式语义如下：

- `continuous` 表示持续按住按键，适合连续挖掘、蓄力或持续使用物品。
- `interval 1` 表示每刻松开并重新按下，不等同于 `continuous`；更大的间隔会在两次脉冲间保持松开状态。
- 停止或切换攻击目标会发送中止挖掘，挖掘完成会发送停止挖掘；停止使用会释放正在使用的物品。
- 使用交互会依次尝试主手和副手；主手未消费动作时才继续尝试副手。
- 实体攻击、实体交互和方块命中使用玩家当前的原版攻击/交互距离，并检查世界边界和方块交互权限。
- 移动通过前进/横移输入交给原版玩家物理处理，潜行时输入会降为 30%，不是直接覆盖水平速度。

#### `/chunkloader` 命令

命令创建的手动加载区域以执行命令时的维度和坐标为中心；可配合原版
`/execute in ... positioned ... run ...` 在任意维度和坐标创建。半径 `0` 只包含中心区块，半径 `r`
包含 `(2r+1)^2` 个区块。内置地图还可以通过画笔创建非矩形区域。

> 当前加载时机是在世界创建后，末影珍珠加载之前

- `/chunkloader`：打开区块加载地图。
- `/chunkloader list`：打开地图内的手动加载区域管理视图。
- `/chunkloader backup`：立即创建一份区块加载配置 JSON 备份。
- `/chunkloader restore confirm`：从最新可读备份恢复配置并重建本模组区块票据。
- `/chunkloader info <名称>`：查看区域维度、区块数和启用状态。
- `/chunkloader add <名称> <半径>`：在当前位置创建并启用一个方形强加载区域。
- `/chunkloader disable <名称>`：撤销该区域的票据，但保留配置。
- `/chunkloader enable <名称>`：根据已保存配置重新添加票据。
- `/chunkloader remove <名称>`：撤销票据并删除区域配置。
- `/chunkloader fake <假人> info`：查看该假人的模拟加载状态和距离。
- `/chunkloader fake <假人> mode player`：切换为玩家模式，使用原版玩家的加载与刷怪语义。
- `/chunkloader fake <假人> mode doll <距离>`：切换为玩偶模式，并设置模拟距离（区块）。

手动区域中的每个区块都会提交等级 31 的强加载票据。票据按原版规则向外自动传播：第 1 圈为等级 32，继续方块刻但不进行实体刻的弱加载；

每个区域使用独立所有者，重叠区域互不撤销；服务端会校验名称、维度、区块数量、预算和编辑版本

内置地图通过 `C` 或 `/chunkloader` 打开，默认处于浏览模式。当前支持平移、滚轮缩放、强加载画笔、擦除、撤销和批量应用，
可在同一页面切换到加载点管理视图；`/chunkloader list` 会直接打开该视图。地图还会显示已启用手动区域、
带名称的玩家与假人位置标记，以及假人实际启用的策略范围。地图设置可调整标记名称大小，
鼠标悬停标记时会显示玩家头像和名称。地图只读取客户端已经加载的区块，
不会为了绘图请求服务端生成新区块。JourneyMap 适配、框选、定位菜单和完整地形瓦片缓存仍在开发中。

## 配置文件

首次加载后会自动生成服务端和客户端配置文件。配置文件中的布尔值使用 `true`/`false`（不要加双引号）。

### 服务端配置

文件位置：`<世界目录>/serverconfig/archweaver-server.toml`
单人游戏位置：`config/archweaver-server.toml`

```toml
[commands]
# 使用 /fakeplayer 和 /player 的最低原版权限等级，范围 0~4。
permissionLevel = 2

[profiles]
# 缓存和在线档案均不可用时，是否允许使用离线 UUID。
allowOfflineProfiles = true
# 玩家档案解析策略：ONLINE_PREFERRED、CACHE_ONLY 或 OFFLINE_ONLY。
strategy = "ONLINE_PREFERRED"

[persistence]
# 服务器启动后是否恢复上次仍在线的假玩家。
restoreFakePlayers = true

[chunkloading]
# 单个区块加载点允许的最大半径，范围 0~32；0 表示仅中心区块。
maxRadius = 8
# 所有启用手动区域允许强加载的区块总数，范围 1~65536。
maxForcedChunks = 2048
# 所有手动强加载点允许完整模拟的区块总数，范围 1~16384。
maxTickingChunks = 512
# 所有在线玩偶模式假人去重后的模拟区块预算，范围 -1~65536；-1 表示不限。
maxPlayerLoadingChunks = 65536

[ui]
# 普通容器是否显示物品转移按钮；假人物品栏始终显示。
enableContainerTransferButtons = true
```

档案策略：

| 策略               | 行为                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------ |
| `ONLINE_PREFERRED` | 优先复用可信正版缓存，否则从 Mojang 获取档案；身份查询失败时根据配置决定是否回退。   |
| `CACHE_ONLY`       | 不发起在线查询，只使用服务器缓存；未命中时根据 `allowOfflineProfiles` 决定是否回退。 |
| `OFFLINE_ONLY`     | 始终按名称生成稳定的离线 UUID。                                                      |

档案解析完成后还会检查同名或同 UUID 在线玩家、封禁列表和白名单。即使服务器使用离线模式，
`ONLINE_PREFERRED` 也会先在后台查询正版档案；只有符合正版账号格式的名称才会继续按 UUID 查询皮肤，
`robot-1` 这类本地名称会直接使用离线档案。皮肤服务单独失败时会保留已确认的正版 UUID 并使用默认皮肤，
不会切换到离线身份。皮肤属性会随驻留记录和预设保存。

### 客户端配置

文件位置：`config/archweaver-client.toml`

```toml
[chunkMap]
# 地图玩家和假人名称的显示缩放，范围 0.5~2.0。
markerNameScale = 1.0
# 是否显示强加载区域外围由票据传播出的弱加载范围。
showWeakLoading = true
```

### 持久化数据

假人的数据分散保存在以下位置：

| 数据                       | 保存位置                                                            | 说明                                                                                                 |
| -------------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| 身份（UUID 与名称）        | `data/archweaver/fake_players.dat`                                  | 驻留清单，用于服务器重启后按身份恢复假人。                                                           |
| 自动化设置（四项开关）     | `data/archweaver/fake_players.dat`                                  | 随驻留记录和预设一起保存，重启后恢复。                                                               |
| 持续动作、移动输入、潜行等 | 预设内快照                                                          | 随预设保存；驻留恢复不携带，启动后由操作者重新设置。                                                 |
| 背包、位置、能力、经验等   | `playerdata/<UUID>.dat`                                             | 由原版玩家数据负责保存和恢复。                                                                       |
| 手动加载区域与假人加载策略 | `data/archweaver/chunk_loaders.dat`（备份在 `archweaver/backups/`） | 保存区域 UUID、名称、维度、非矩形区块集合，以及假人 UUID、启用状态和模拟距离；活动票据在启动后重建。 |

## 其他
