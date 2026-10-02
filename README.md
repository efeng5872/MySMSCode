# MySMSCode

一款 Android 短信转发应用：按发送方和关键词筛选新收到的短信，将命中内容发送到配置的飞书或企业微信群机器人。

## 主要功能

- **转发规则**：配置发送方、关键词及关联机器人，支持规则启停和号码标准化；同一规则内任一关键词命中即可匹配，关键词不区分大小写。
- **机器人管理**：配置飞书和企业微信群机器人的 Webhook，支持多个机器人。
- **监控管理**：启动或停止短信监控，通过前台服务处理短信。
- **历史与重试**：查看短信处理记录、转发结果和失败原因，对符合条件的失败任务自动重试，也可手动重试。
- **模拟验证**：通过模拟注入验证规则匹配和转发流程。
- **保活与诊断**：提供电池优化设置引导、运行状态诊断、开机和应用升级后的恢复，以及周期看门狗检查。

## 开始使用

需要 Android 10（API 29）或更高版本，以及可访问机器人 Webhook 的网络。

1. 从源码构建并安装应用，首次使用时按应用提示授予短信接收权限及适用的通知权限。
2. 添加飞书或企业微信群机器人，填入对应 Webhook。
3. 创建发送方和关键词规则，关联机器人并启用规则。
4. 启动监控，按保活向导检查系统后台运行设置。
5. 使用模拟注入或一条测试短信检查匹配结果，并确认目标群收到消息；失败详情可在历史记录中查看。

转发内容包含发送方、接收时间、匹配关键词和短信正文。配置规则前请确认目标群的可见范围；Webhook 按凭据管理，不要提交到代码仓库。

后台运行和恢复受 Android 及手机厂商的限制，周期检查不保证固定恢复时限。当前未支持首次解锁前的开机恢复；各机型的效果需要实际验证。

## 从源码构建

项目使用 Kotlin、Jetpack Compose、Room、WorkManager 和 libphonenumber。构建配置声明 Gradle 9.3.1、AGP 9.1.0、compile SDK 36.1、target SDK 36；Gradle Daemon 工具链声明为 JDK 17。完整版本信息见[项目概览](docs/项目概览.md)。

使用 Android Studio 打开项目，准备配置要求的 JDK 和 Android SDK，再通过 Gradle Wrapper 构建。Windows PowerShell 示例：

```powershell
.\gradlew.bat :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

已安装应用的升级包必须使用相同签名；本项目维护者的既有签名和安装约束见 [AGENTS.md](AGENTS.md)。

## 验证

单元测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Android 集成测试需要已连接的模拟器或测试设备：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

构建和测试命令为项目入口，本次 README 更新没有运行应用测试。历史测试结果与当前验证状态分别见[测试报告索引](docs/README.md)和[项目当前状态](PROJECT_STATUS.md)。

## 项目文档

- [项目概览](docs/项目概览.md)：模块入口、技术栈和环境说明。
- [知识库索引](docs/README.md)：需求、设计、测试报告及维护资料。
- [当前状态](PROJECT_STATUS.md)：当前任务、验证证据和待处理事项。

仓库地址：[efeng5872/MySMSCode](https://github.com/efeng5872/MySMSCode)。
