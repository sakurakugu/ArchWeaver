# 项目概览

ArchWeaver 是一个面向 Minecraft 的辅助模组，当前适配 Minecraft 26.1.2 / NeoForge。主要功能包括假人、区块加载。

项目发布两个模组：

- **ArchWeaver**（`archweaver`）：纯功能模组，不注册任何方块、物品、实体，可随时装卸而不损坏存档。
- **ArchWeaver: Artifice**（`archweaver_artifice`）：内容模组，注册方块物品，硬依赖 ArchWeaver。

分界线是**是否往 Minecraft 注册表里加东西**：注册项会写进存档，卸载即数据损坏，所以必须留在内容版。

项目采用 Gradle 多模块结构：

- `common`：核心共享业务逻辑，以及主要单元测试。
- `api`：对外公开 API（`com.sakurakugu.archweaver.api`），随核心 jar 发布。
- `neoforge`：核心 NeoForge 平台入口，嫁接 `common` + `api` 源码后产出 `archweaver` jar。
- `artifice/common`、`artifice/neoforge`：内容版，产出 `archweaver_artifice` jar。
- `tools`：开发辅助脚本。

# 开发约定

1. Java 找不到就在 C:\Software\Deps\Java\
2. 注释用中文
3. Gradle 首次编译或解析依赖可能耗时较长，执行编译命令时超时时间至少设置为 120 秒
4. 不用兼容旧的数据，需要兼容时会亲自说的
5. 其他相似相关mod 在 `other/参考mod`
6. 内容版要用核心的能力时，在 `api` 里加门面，不要直接暴露 `common` 的内容。
7. 开发启动用 `.\gradlew.bat :artifice:neoforge:runClient`（会把核心一起带上）；
   裸 `runClient` 会同时启动两个客户端。只测核心用 `:neoforge:runClient`。
