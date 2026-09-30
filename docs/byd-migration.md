# BYD 最新版迁移清单

参考用户提供的迪控 26.0928 反编译源码与资源。此分支的目标已扩大为最新版全部功能；下表记录真实接入程度，不能将当前 APK 称为完整复刻版。

## 已接入，尚需设备 / 实车验证

| 功能 | 当前实现 | 原版入口 |
|---|---|---|
| 独立 BYD 标签 | 使用 GKD 的底部导航、卡片和分层设置，搜索结果不再铺在主页 | SimpleMainActivity |
| 权限管理 | 附近设备、通知、无障碍、电池优化、定位；后台与锁屏权限为明确的用户确认项 | SimpleGuideActivity |
| 新手指引 | 权限 → 控车方式 → 蓝牙与往返实测；小组件和 Auto 分支 | SimpleGuideActivity |
| 选择车辆蓝牙 | 名称筛选 / 全部设备、地址确认、手动填写 | SimpleDeviceActivity |
| 主 / 关窗组件 | Provider 预览、样式折叠、独立绑定，分别标记解锁、上锁、空调、熄火和关窗 | r10、FloatingWidgetActivity |
| 靠近 / 远离 | 阈值、确认次数、初始信号增减量、冷却、阈值重叠时的临时抑制、口袋补偿 | BluetoothService、SimpleAutoUnlockActivity、SimpleAutoLockActivity |
| 扫描与省电 | BLE 扫描、GATT RSSI 轮询、混合模式、静止暂停与显著运动恢复、分段扫描、锁屏限制 | SimpleBleParamActivity |
| 信号诊断 | 首页、启动、蓝牙、阈值及日志页显示 RSSI、更新时刻、有效样本数、当前扫描方式及未触发原因；可选混合模式 GATT 无样本 15 秒退回 BLE 30 秒（默认关闭，以保留原版时序） | BluetoothService、运行日志 |
| 连接省电制约 | 多 SSID、普通蓝牙、车机多媒体蓝牙：命中连接即停扫，不受门锁或电源状态影响；被动监听全部制约解除恢复 | BluetoothService.C0 / l / m0、SimpleWifiConstraintActivity、SimpleBluetoothConstraintActivity、SimpleCarBluetoothActivity |
| 上下电 BLE 分支 | 独立电源状态确认、阈值、连续确认、解锁后等待、下电指令后关窗；默认关闭 | SimpleAutoPowerOnActivity、SimpleAutoPowerOffActivity |
| 大陆 Auto 登录 | WBSK → AES 登录及签名，手动选择品牌和车辆，会话使用 Android Keystore 加密保存 | ys.G0、ys.O0、ys.B |
| Auto 云控 | 解锁、上锁、开启 / 关闭空调、关窗，通过云端确认指令受理 | ys.v、rx.b |
| Auto 车况 | 按需请求 / 查询结果，电量、续航、门锁、电源档位、胎压等 | ys.D 的 smali |
| 日志与配置 | 内存日志、系统文件选择器导入导出；导入清除设备专属组件绑定 | SimpleConfigManageActivity |

### 已知差异

- 所有自动控车默认不运行。启动需用户确认车门状态；进程死亡不推断车端状态并自动恢复。
- 当前靠近 / 远离实现保留了最短 2 秒确认、启动时先有远处证据及连续采样要求，与原版全部过滤分支尚非逐指令一致。
- 口袋状态由近距传感器遮挡判断，不具备原版所有亮度 / 姿态处理分支。
- Auto 当前只有大陆账号密码与云控。网络“上电 / 下电”对应原版 OPENAIR / CLOSEAIR，即开启 / 关闭空调，不保证等同于所有车型的整车电源切换。
- 组件 `performClick` 和云端 code=0 都不能证明车辆已执行；物理状态确认、二次上锁还未接入自动流程。
- GATT 读取能否连接取决于车辆。系统守候和智能模式没有伪装成现有扫描模式。
- 当前上下电仅接入控车 BLE 信号分支；原版默认车机蓝牙、开门触发与 Auto 接管并未完整移植。当前 BLE 下电默认 -95 dBm 不等于原版车机默认 -20 dBm。
- 编译及纯逻辑测试不等于 vivo 后台、官方小组件及实车验证。

## 全量范围中尚未接入

| 分类 | 必须继续移植的功能 | 原版入口 |
|---|---|---|
| 其他控车方式 | 手表 App、官方 App 无障碍、ROOT、Android 15 旧方式、外部广播、自定义 ROOT / HTTP 指令、直接蓝牙钥匙 | SimpleControlMethodActivity、r10 |
| Auto | 扫码登录、境外账号、多账号、凭据导入 / 共享、BLE 协议认证、BLE / 网络补救、身份 / PIN 纠错、车辆监测 | SimpleAutoCloudSettingsActivity、ys、rx、xl |
| 上下电 | 车机蓝牙分支、开门 / 延迟触发、Auto 接管及车端实际状态反馈 | SimpleAutoPowerOnActivity、SimpleAutoPowerOffActivity |
| 上锁增强 | 断连二次上锁、官方通知反馈、关窗补救、熄火后才上锁 | SimpleAutoLockActivity、BluetoothService |
| 扫描增强 | 系统守候、智能切换、滤波、运动确认 / 缓冲、预唤醒、MQTT 信号发布 | SimpleBleParamActivity |
| 条件约束 | 静默时段、星期 / 节假日、地点标记；车机蓝牙与自动下电的完整联动 | SimpleWifiConstraintActivity、SimpleBluetoothConstraintActivity、SimpleCarBluetoothActivity、SimpleQuietTimeActivity、SimpleLocationMarkerActivity |
| 按键 | 音量键单击 / 双击 / 长按、自定义键、摇动、无音频 / 锁屏及条件限制 | SimpleKeyControlActivity、SimpleKeyManageActivity、VolumeKeyService |
| 签到 | 官方 App、快捷方式、Auto 云端、补签、测试、静默、返回与备选流程 | SimpleDailySignActivity、BluetoothSignService |
| 预启动 | 官方 App 启动 / 连接 BLE、组件刷新、锁屏限制、重试 | SimplePreLaunchActivity |
| 预约 | 定时、星期 / 地点条件、消息关键词触发上电 | SimpleReservationActivity、TimeBasedService、NotificationListener |
| 远程 | 巴法云三种角色、常在线 / 临时 / 消息唤醒、状态反馈 | SimpleRemoteControlActivity |
| 车机 | RFCOMM、热点 HTTP、巴法云与车机配套程序 | SimpleCarInteractionActivity |
| 声音 | 七类事件、文本 / 音效 / 组合、音频通道、音量 | SimpleControlSoundActivity |
| 桌面与浮窗 | 主题、车辆信息 / 胎压、快捷操作、透明度、浮窗及摇动 | WidgetSettingsActivity、CommandWidgetProvider |
| 保活与重启 | 与 GKD 保活机制整合、受系统限制时的恢复流程与状态核对 | SimpleKeepAliveActivity、BootReceiver |
| 商业入口 | 原版激活、内测、授权及其他业务入口的处理方案 | MainActivity、o81 |

当前尚未接入的页面会显示“迁移尚未完成”，没有可保存但不执行的开关。后续每项必须完成配置、运行调用及验证后再改为可用。

## Auto 协议依据

- `qt1.P / r` 的 MD5 输出为**大写**十六进制。
- `ys.i1 / D1` 的登录 AES key 是 **MD5(MD5(password))**，不是 MD5(username)。后者仅用于 imeiMD5。
- AES/CBC/PKCS5Padding 使用零 IV；这是现有外部协议兼容要求。
- 签名为字段按键名排序，拼接 `&password=...`，SHA-1 字节交替大写 / 小写并删除每字节前导 0。
- 外层 JSON 的字段顺序参与 SHA-256 checkcode；WBSK 包裹外层请求及响应。
- 登录版本字段 502 / 9.10.2，其他大陆请求 version=531；协议固定 imei=BANGCLE01234。
- WBSK 的来源哈希在 `byd-wbsk-provenance.json`。移植仅折叠混淆常量、重命名类型、去掉无关依赖及修复 JADX 在类初始化器中的非法 `return`。
- 没有读取原 app 保存的账号、密码或会话；未向真实服务器发送测试登录 / 控车请求。

## 验证要求

1. `gkdDebug` 编译与单元测试。
2. 在关闭 GKD 自动化并退出自动化模式后验证界面；测试结束恢复原值。
3. 官方 App 的实际组件绑定、配置与息屏执行。
4. vivo 真机后台授权与往返实测。
5. 用户主动输入账号后验证登录 / 选车 / 车况；实车指令由用户明确确认。

## 2026-09-30 信号诊断修订

- 用户已实测小组件手动控车正常，但自动靠近 / 远离没有执行；界面只显示运动恢复。具体根因尚未确定，不能把运动事件当成车辆信号已收到。
- 增加信号卡片和每 5 秒一条的信号 / 判断日志。上次信号与新信号分开标记；超过 10 秒无样本不延续确认计数。
- 修复混合模式 GATT 无 RSSI 时持续重试而不退回扫描的问题；显式 GATT 模式保留用户选择。
- 连续扫描不再每 30 秒重启，模式和运动条件每秒重新评估。设备地址比较不区分大小写。
- 修复旧 GATT 回调清空新连接读取标记的问题；无效正值 RSSI 不参与判断。有效的较强 / 较弱负值信号不再被旧的 -110～-20 限制忽略。
- 最终 gkdDebug 构建通过，151 项单元测试通过。实车自动触发尚未验证。

## 2026-09-30：连接制约与扫描时序

依据用户提供的设置页及运行日志，逐项对照 recovered.dex 的正常 JADX 输出：

- `BluetoothService.Z1`：`scan_sleep_seconds` 默认 2，范围 1～5；扫描窗口为静默时长的 2.5 倍，最少 5 秒。默认扫描 5 秒、静默 2 秒，日志每 7 秒一轮。
- `a2 / b2`：扫描窗口使用 LOW_LATENCY、ALL_MATCHES、AGGRESSIVE、最多匹配数和零批处理延时。这里的“低功耗”来自窗口间停扫，不等于使用 Android 的 LOW_POWER 扫描参数。亮屏 callback；BLE 模式息屏使用显式非导出 Receiver 的 PendingIntent，停止使用同一 PendingIntent 并取消，隔离旧扫描回调。短唤醒锁仅覆盖扫描窗口及 2 秒余量。
- `R1 / a.bp`：默认 GATT 连接尝试最多 5 秒，失败 / 断开后静默 2 秒。连接成功后默认每 500 毫秒读取 RSSI。约 5 分钟的失败轮次后清理连接重试；前 5 轮及每 10 轮记录日志。
- `a.lp`：连接后的前 6 次读取检查为宽限期；随后连续 3 次没有新信号，清理 GATT 连接并重新轮询。不会用合成弱信号充当实测信号。
- 原版 `C0 / l` 包含门锁及 Wi-Fi 信号阈值分支。按照用户后续明确修正，当前连接指定家 / 公司 SSID 即立即停扫，不等待上锁，不使用信号阈值放行；旧配置中的模式字段在加载时忽略。
- 普通蓝牙及车机多媒体蓝牙命中连接均立即停扫，不等待解锁。所有启用的连接制约都解除后恢复。

实际暂停会停止扫描 / GATT 并注销连续加速度与近距监听。连接制约等待系统连接事件，静止暂停等待显著运动事件，没有每秒的暂停轮询。连接监听不扫描 Wi-Fi、不请求 GPS，也不会主动连接制约蓝牙。全部连接制约关闭时不注册连接监听。

### 保留的差异与限制

- 用户要求连接制约优先：命中指定连接后暂停所有自动蓝牙监测，也不等待下电。原版车机蓝牙专用下电信号分支仍未完成迁移。
- 启动时通过公开接口读取音频及 GATT 已连接设备；其他蓝牙类型依赖之后的 ACL 连接事件。没有复制原版的隐藏 `BluetoothDevice.isConnected` 反射，已连接的其他类型设备可能需要重新连接。
- SSID 因权限不可读时不能判断是否命中指定网络：保留扫描但禁止解锁，界面显示名称不可读及授权入口。
- 本次没有复制原版系统守候、智能切换、精确闹钟扫描守护、运动 3 秒确认 / 5 分钟缓冲以及所有 Auto 接管分支。暂停时保留当前已确认 / 估计的门锁状态，GATT 丢信号只重建连接，不重启整个服务去猜车门状态。
- 初始和已锁的近车 BLE 监测在最近 5 秒有信号时保持连续扫描；待远离上锁时不分段停扫。未收到弱信号时不会仅凭扫描丢失就发送上锁。
- 这不是完整复刻版。真实蓝牙扫描、vivo 息屏权限、设备连接事件及车端执行仍需实机验证。

本次验证：首次全量 163 项单测通过；修正版 Windows 文件占用失败的原有设置测试及全部车辆测试单独复跑，37 项通过。最终 gkdDebug 构建通过。MuMu 实际打开 Wi-Fi 停扫 / 信号制约、普通蓝牙、车机配对选择和扫描参数页，截图及语义标签已核对；临时自动化开关、运行模式和 Wi-Fi 模式已恢复。实车 / vivo 息屏仍未验证。

用户修正后的连接即暂停版本：8 项制约回归测试及 gkdDebug 构建通过；MuMu 核对 Wi-Fi 即时暂停、车机多媒体蓝牙说明与配对弹窗。界面测试前关闭自动化，结束后恢复并核对原开关和运行模式。尚未实车验证。
