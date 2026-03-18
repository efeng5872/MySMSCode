# 阶段二测试报告

## 基本信息
- 报告日期：2026-03-18
- 测试环境：Android 模拟器 `Medium_Phone_API_36.1`
- 构建版本：当前工作区 Debug APK
- 测试目标：验证权限、前台服务、真实短信接收、飞书/企业微信真实 Webhook 转发，以及国际号码标准化匹配

## 测试范围
- 首次安装后的运行时权限申请流程
- 前台服务（Foreground Service）启动与常驻能力
- 真实短信接收与处理链路
- 飞书机器人真实 Webhook 成功路径
- 企业微信群机器人真实 Webhook 成功路径
- 双通道同时转发成功路径
- 国际号码标准化（E.164）匹配能力

## 测试执行结果

### 1. 权限申请流程验证
- 已验证 `RECEIVE_SMS`、`READ_SMS`、`POST_NOTIFICATIONS` 三项权限申请流程。
- Fresh install 场景下，界面会先显示权限未就绪状态。
- 权限授予后，界面会切换为权限已就绪，并允许继续启动监控。

### 2. 前台服务启动验证
- 已验证点击启动监控后，`MonitoringForegroundService` 可以被拉起。
- 已通过 `dumpsys activity services` 确认服务处于运行状态。
- 已验证补充 `android.permission.FOREGROUND_SERVICE_DATA_SYNC` 后，Android 16 环境下不再因权限缺失崩溃。

### 3. 真实短信接收链路验证
- 已验证模拟器接收真实短信广播后，应用可以进入完整处理链路。
- 关键日志链路已确认：
  - `incoming_sms`
  - `service_start`
  - `processing_result`
  - `persistence_result`

### 4. 飞书真实 Webhook 验证
- 已使用真实飞书群机器人 Webhook 完成成功路径验证。
- 测试短信内容包含飞书群安全关键字 `test`。
- 验证短信内容如下：
  - `test verification code is 778899`
- 应用侧关键结果：
  - `incoming_sms sender=10690001 parts=1 length=32`
  - `processing_result source=REAL_SMS sender=10690001 status=PENDING_FORWARD attempts=1 keyword=code`
  - `persistence_result source=REAL_SMS sender=10690001 recordStatus=SUCCESS attempts=1 failure=n/a`
- 数据库存证结果：
  - 最新 `sms_records` 记录状态为 `SUCCESS`
  - 最新 `forward_attempts` 记录为 `FEISHU / SUCCESS / response_code = 200 / response_message = success`
- 群侧人工确认已收到消息，内容如下：
  - `Sender: 10690001`
  - `ReceivedAt: 1773576825411`
  - `MatchedKeyword: code`
  - `Message: test verification code is 778899`

### 5. 企业微信与双通道真实转发验证
- 已使用真实企业微信群机器人 Webhook 完成成功路径验证。
- 已验证同一条短信同时转发到飞书与企业微信双通道。
- 验证短信内容如下：
  - `test verification code is 990011`
- 应用侧结果：
  - 处理结果为 2 个 channel attempt，最终 `recordStatus = SUCCESS`
- 数据库存证结果：
  - `FEISHU / SUCCESS / response_code = 200 / response_message = success`
  - `WECOM / SUCCESS / response_code = 200 / response_message = ok`
- 群侧人工确认企业微信群已收到消息，内容如下：
  - `Sender: 10690001`
  - `ReceivedAt: 1773577389904`
  - `MatchedKeyword: code`
  - `Message: test verification code is 990011`

### 6. 飞书业务响应判定回归验证
- 已验证应用不再仅以 HTTP `200` 作为飞书成功判定条件。
- 已补充飞书响应体 `code` / `msg` 解析逻辑。
- 已验证“HTTP `200` 但业务失败”的场景会被正确记为 `FAILED`，避免误判为成功。

### 7. 国际号码标准化（E.164）模拟器验证
- 验证日期：2026-03-18
- 验证目标：确认规则中保存本地中国手机号时，来信号码使用国际格式 `+86` 仍可被正确匹配。
- 前置配置：
  - 新增规则号码：`13608083211`
  - 关键字：`code`
  - 目标机器人：飞书 + 企业微信
- 发送测试短信：
  - 发送方：`+8613608083211`
  - 内容：`test verification code is 112244`
- 关键日志结果：
  - `incoming_sms sender=+8613608083211 parts=1 length=32`
  - `processing_result source=REAL_SMS sender=+8613608083211 status=PENDING_FORWARD attempts=2 keyword=code`
  - `persistence_result source=REAL_SMS sender=+8613608083211 recordStatus=SUCCESS attempts=2 failure=n/a`
- 结论：
  - 规则中配置的本地号码 `13608083211` 已能够命中国际格式来信号码 `+8613608083211`
  - 基于 `libphonenumber` 的 E.164 标准化匹配已在模拟器环境验证通过

## 测试证据
- `logcat` 已捕获完整短信处理与持久化日志。
- 数据库已验证真实飞书成功记录与双通道成功记录。
- 飞书群与企业微信群均已由人工确认收到真实消息。
- 2026-03-18 已新增国际号码标准化模拟器日志证据。

## 覆盖到的测试计划条目
- TC-03：真实短信接收与处理链路
- TC-04：飞书真实 Webhook 成功路径
- TC-05：企业微信真实 Webhook 成功路径
- TC-05A：双通道同时转发成功路径
- TC-08A：飞书业务失败响应判定回归
- TC-09：运行时权限申请与前台服务启动
- TC-12：国际号码标准化匹配回归

## 当前结论
- 模拟器环境下，阶段二核心链路已验证通过。
- 飞书、企业微信、双通道、权限、前台服务、真实短信接收均已完成验证。
- 国际号码标准化匹配已在模拟器环境验证通过。
- 当前系统已具备继续进行界面优化与后续部署准备的基础。

## 仍建议继续验证的项目
- 真机环境下再次回归国际号码标准化匹配。
- 真机环境下验证不同国家号码样例。
- Android 13+ 与更高系统版本的通知权限交互差异。
- 更长期运行下前台服务稳定性与自动重试持续行为。
