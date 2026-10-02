# 项目概览

ArchWeaver 是一个面向 Minecraft 的辅助模组，当前适配 Minecraft 26.1.2 / NeoForge。主要功能包括假人、区块加载。

项目采用 Gradle 多模块结构：

- `common`：共享业务逻辑，以及主要单元测试。
- `neoforge`：NeoForge 平台入口。
- `tools`：开发辅助脚本。

# 开发约定

1. Java 找不到就在 C:\Software\Deps\Java\
2. 注释用中文
3. Gradle 首次编译或解析依赖可能耗时较长，执行编译命令时超时时间至少设置为 120 秒
4. 不用兼容旧的数据，需要兼容时会亲自说的
5. 其他相似相关mod 在 `other/参考mod`
