# NanaHopper

一个支持 Minecraft 1.21+ 版本的 PaperMC 插件。

## 项目特性

- ✅ **支持 Minecraft 1.21 到 26.2 (所有未来版本)**
- ✅ 基于 PaperMC API 开发,使用向后兼容策略
- ✅ 支持 Folia 调度器
- ✅ 使用 Java 21 编译(兼容 Java 21 和 Java 25 服务端)
- ✅ Gradle 构建系统

## 环境要求

- **JDK**: Azul Zulu JDK 21 或更高版本
- **Gradle**: 8.13+ (已通过 Gradle Wrapper 管理)
- **Minecraft**: 1.21+ 版本

## 快速开始

### 构建项目

```bash
# Windows
gradlew.bat build

# Linux/Mac
./gradlew build
```

### 本地测试服务器

```bash
# Windows
gradlew.bat runServer

# Linux/Mac
./gradlew runServer
```

这将启动一个本地的 Minecraft 1.21.4 测试服务器,插件会自动加载。

### 生成发布包

```bash
# 构建 JAR 文件
gradlew build

# 输出的 JAR 文件位于: build/libs/NanaHopper-0.1.0.jar
```

## 配置

### 修改 Java 版本

在 `gradle.properties` 中修改 `targetJavaVersion`:

```properties
targetJavaVersion=21
```

### 修改测试服务器版本

在 `build.gradle` 中修改 `runServer` 任务的 `minecraftVersion`:

```groovy
tasks {
    runServer {
        minecraftVersion("1.21.4")  // 修改为你想要的版本
    }
}
```

### 可用 Paper API 版本

本项目使用 **向后兼容策略**: 使用最低版本 API 编译,产物可在所有更高版本运行。

**当前配置:** `1.21.1-R0.1-SNAPSHOT`

这个版本可以兼容:
- ✅ Minecraft 1.21.x
- ✅ Minecraft 1.21.x (所有小版本)
- ✅ Minecraft 26.1 (小鬼当家)
- ✅ Minecraft 26.2 (混沌立方) - 当前最新
- ✅ 未来的 27.x, 28.x 等版本 (只要 API 保持兼容)

**原理:** Bukkit/Paper 的设计保证向后兼容性 - 使用旧版 API 编译的插件可以在新版服务端上运行,
只要不使用新版本独有的 API 即可。

如需升级到特定版本的 API (例如新版 Paper 引入了必须使用的特性),可在此查看可用版本:
[PaperMC Maven Repository](https://repo.papermc.io/repository/maven-public/io/papermc.paper/paper-api/)

## 项目结构

```
NanaHopper/
├── src/
│   ├── main/
│   │   ├── java/top/imbring/nanaHopper/
│   │   │   └── NanaHopper.java          # 主插件类
│   │   └── resources/
│   │       └── plugin.yml                # 插件描述文件
│   └── test/
├── build.gradle                          # 构建配置
├── gradle.properties                     # 环境配置
└── settings.gradle                       # 项目设置
```

## 开发指南

### 添加新功能

1. 在主插件类 `NanaHopper.java` 中初始化你的功能
2. 在 `onEnable()` 方法中注册事件、命令等
3. 使用 `getServer().getPluginManager().registerEvents()` 注册监听器

### 最佳实践

- 使用 `@Override` 注解确保正确重写方法
- 启用 deprecation 和 unchecked 警告检查
- 遵循 Java 编码规范
- 为复杂功能编写单元测试

## 常见问题

**Q: 如何支持特定版本的 Minecraft?**

A: 修改 `build.gradle` 中的 Paper API 依赖版本,并调整 `plugin.yml` 中的 `api-version`。

**Q: 插件没有加载怎么办?**

A: 检查 `plugin.yml` 配置是否正确,确保主类路径准确无误。

**Q: 如何在生产环境中使用?**

A: 运行 `gradlew build`,然后从 `build/libs/` 目录复制 JAR 文件到你的服务器 plugins 目录。

## 许可证

本项目仅供学习和开发使用。

## 联系方式

开发者: Block_Bring
