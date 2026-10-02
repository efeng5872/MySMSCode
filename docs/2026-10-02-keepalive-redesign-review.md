# 保活重设计方案复核

- 日期：2026-10-02（Asia/Shanghai）。
- 复核对象：本聊天提出的“事件触发恢复 + 实时健康检查 + 低频看门狗兜底”方案。
- 基线：`6cc79e6`；应用源码与 `d778433` 一致。
- 用户目标：系统允许时尽快恢复，兼顾耗电。
- 方法：静态源码核对、并发交错推演、Android 与 Kotlin 官方文档核实。未执行应用构建、故障注入或真机测试。
- 结论：方向可保留，但存在 6 项需要补充的设计问题。以下是设计风险及修订建议，不代表已复现全部运行问题，也不代表已批准实施。

## 1. 高优先级：停止优先必须由原子状态转换保证

原方案仅提出“启动前再检查停止状态”，仍存在检查后用户停止、旧恢复请求随后执行的窗口。

现有代码依据：

- [MonitoringWatchdogWorker.kt](../app/src/main/java/com/example/mysmscode/MonitoringWatchdogWorker.kt) 的 `recoverMonitoring` 会把之前读取的 `checkedState` 整行保存。
- [MonitoringForegroundService.kt](../app/src/main/java/com/example/mysmscode/MonitoringForegroundService.kt) 的 `ACTION_START_MONITORING` 调用 `persistMonitoringStarted`。
- [MonitoringRecovery.kt](../app/src/main/java/com/example/mysmscode/domain/MonitoringRecovery.kt) 的 `markMonitoringStarted` 将 `monitoringEnabled` 设为 true、`stoppedByUser` 设为 false。
- 服务内的 `monitoringStateMutex` 不覆盖 Worker、页面等其他写入者。

静态推演：Worker 读取“启用”状态 → 用户停止并保存 → Worker 用旧快照回写 → 恢复命令重新启用监控。取消 WorkManager 任务不能作为阻止所有已发命令的唯一保证。

修订建议：

1. 分离用户期望状态和运行观测，诊断写入只能更新诊断字段，不能携带旧启停状态回写。
2. 用户启停更新持久化控制版本号；自动恢复携带读取时的版本，在事务中校验“仍启用且版本一致”。
3. 服务收到请求时再次校验版本；显式启用与自动恢复使用不同语义，自动恢复不得清除用户停止意图。
4. 停止、恢复接纳和就绪回执由统一协调逻辑处理，过期请求及回执均失效。不能持数据库事务等待系统调用或网络请求。

验收：覆盖停止发生在检查前、检查后、请求发出后和就绪回执前的交错；最终均保持用户最后一次选择。

## 2. 高优先级：自动重启可能扩大丢失或重复发送窗口

原方案提出对疑似卡住的处理组件进行取消或重建，但没有落实发送任务持久化及不确定发送结果的处理方式。

现有代码依据：[MonitoringForegroundService.kt](../app/src/main/java/com/example/mysmscode/MonitoringForegroundService.kt) 的 `handleIncomingSms` 先调用 `webhookDispatcher.dispatch`，然后才 `saveOutcome`；[IncomingSmsReceiver.kt](../app/src/main/java/com/example/mysmscode/IncomingSmsReceiver.kt) 通过 Intent 传递短信，没有先在此入口保存待执行任务。

因此进程退出可能留下“尚未保存任务”或“服务端已收到，但本地结果未保存”的窗口。恢复服务本身不会补齐这些记录；在发送状态不明时自动重发可能重复送达。

另一个边界：[WebhookPayloadFactory.kt](../app/src/main/java/com/example/mysmscode/network/WebhookPayloadFactory.kt) 使用同步 `HttpURLConnection`。现有连接、读取超时不能直接解释为整个请求的总超时；简单包一层协程 `withTimeout` 也不保证立即终止阻塞调用。[Kotlin 官方说明](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout.html)

修订建议：第一轮只自动恢复已确认缺失的服务，对仍存在但疑似卡住的服务记录异常，暂不强制重启或自动重放任务。后续若增加自动重建，先设计待处理任务持久化、执行所有权、可验证的取消行为、结果不明状态及重复控制；无法确认机器人端具备幂等能力时，不承诺严格只送达一次。

验收：覆盖请求前、发送中、服务端已收但本地未保存三个故障点，以及旧任务取消后仍返回结果的情况。

## 3. 中优先级：探测成功和服务缺失都需要明确判据

当前服务在独立协程中写心跳，并为短信与重试分别启动工作。一个新增的独立探测协程回复成功，仍不能证明所有业务任务健康。

此外，Manifest 没有为这些组件配置独立进程；当前 `onBind` 返回 null。不能假定已有可用的绑定探测接口，也不能承诺进程整体冻结或主线程阻塞时看门狗仍能独立检查和修复。

修订建议：

- 当前进程内维护服务实例标识及 `STARTING / READY / STOPPING` 生命周期；进程重新创建后旧内存状态自然失效。
- 生命周期登记与恢复协调器的“启动请求待确认”一起判定，不把短暂尚未登记的启动过程视为退出。不把带自动创建行为的绑定当作纯查询。
- 明确探测等级：服务就绪只表示生命周期及必需本地初始化完成；业务健康另查任务进展和错误。初始化确认不等待所有网络重试完成。
- 第一轮界面只显示“服务已就绪”“业务异常待检查”等可验证状态，不用单一健康标记替代所有结论。
- 处理链路探测作为后续范围，应区分空闲、正常忙碌、断网和真正无进展，避免队列长就误判卡死。

验收：覆盖冷启动、启动中重复检查、服务退出重建、旧实例迟到回执、主线程阻塞、断网与正常长任务。

## 4. 中优先级：恢复时效不能省略系统调度和启动限制

取消 45 分钟门槛只能减少“检查已运行后”的等待，不能缩短看门狗尚未获调度的时间。WorkManager 周期最小间隔为 15 分钟，实际执行会受系统优化和约束影响。[Android 官方说明](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#schedule_periodic_work)

Android 12 及以上的后台前台服务启动需要满足适用的例外条件。电池优化豁免只是其中一种；统一协调器不能把所有入口都当作同一种后台场景。[Android 官方说明](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

修订建议：保留每个触发来源的上下文，合法条件下尝试启动并捕获系统拒绝；异步启动失败由就绪超时确认。明确记录三个阶段：发现时间、发出恢复请求时间、就绪时间。真实退出时刻未知时记录未知，不用最后心跳伪造准确中断时长。

短暂技术失败与持续受限分开处理：前者在获调度机会时有限重试；后者记录受阻、通知去重，并提供应用内状态。通知权限或渠道关闭时不能把调用 `notify` 当成用户已收到提示。

验收：分别测页面触发、短信触发、开机/升级、周期任务，在后台受限和通知关闭时都应有正确状态。不得承诺分钟级硬性恢复上限。

## 5. 中优先级：需要区分不同的用户停止来源

现有“用户主动停止”主要由应用内停止操作写入。Android 13 的活动应用面板停止会结束整个应用，不提供回调，而已调度的任务和闹钟仍可能执行；官方建议下次运行时检查进程退出原因。[Android 官方说明](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)

修订建议：验收中分别覆盖应用内停止、活动应用面板停止、系统设置强行停止、划掉最近任务以及进程异常退出。结合新退出记录与最近显式启用记录判定，不能把历史 `USER_REQUESTED` 永久当作当前停止意图。证据不足时标为需用户恢复，不猜测异常退出；普通划掉最近任务也不能自动等同于明确停止监控。

该部分需要目标机型验证和明确产品行为，不仅是取消一个周期任务。

## 6. 中优先级：探测超时要处理时钟调整与休眠

当前心跳判定使用 `System.currentTimeMillis()` 差值，用户调时或系统校时可改变该差值。引入更短的阈值后，时钟和休眠引起的误判更明显。

修订建议：运行时的探测、启动宽限与冷却使用单调时钟，并限定在同一次启动或进程实例内比较。`elapsedRealtime` 包含深度休眠，重启后重新计时；跨重启不能直接比较旧值。[Android 官方说明](https://developer.android.com/reference/android/os/SystemClock)

心跳变旧仅触发进一步核对。唤醒后服务存在时给它一次新探测机会，不因休眠期间没有执行心跳就直接重启。日志继续使用本地可读日期时间。

验收：模拟时钟前跳/后跳、熄屏休眠、重启、旧实例回执及冷却期间重复触发。

## 修订后的建议范围

第一轮建议实现：用户期望与运行状态分离、原子恢复接纳、失效请求拦截、服务实例及启动宽限、就绪确认、周期检查时缺失即恢复、受限诊断和通知去重。继续复用短信/页面/开机/升级事件及低频周期任务。

第一轮不自动强制重启疑似卡住的在途处理任务。业务任务持久化、可取消网络与重复控制需要后续单独设计，完成后再评估自动重建。

本报告应作为后续需求和技术设计的输入。尚未确定短探测、启动宽限及退避参数的验收数值；应通过基线测量选择，不能照搬 45 分钟或随意改为 1 分钟。
