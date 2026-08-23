# Samsung Region Override

简体中文 | [English](README.md)

这是一个通过 Shizuku 临时修改 Android 向应用报告的 SIM 地区信息的工具。推荐工作方式不是长期保持，而是：**开启伪装 → 使用目标应用 → 立即结束并恢复**。

项目以较新的三星手机为主要支持目标。目前完整实测范围只有 **SM-S938B、Android 16、One UI 8.5、Shizuku 13.1.5**；其他三星或非三星设备属于兼容目标，不代表已经验证。

它不需要 root，基带仍接入真实运营商，手机号也不会改变；但 Android 电话框架中的运营商身份、CarrierConfig、数据配置和 IMS 行为都会受到影响，因此不能把它当作纯显示修改。

## 60 秒使用流程

1. 启动 Shizuku，并向本应用授权。
2. 选择 SIM、目标地区和目标应用所需的层。
3. 点击**开始伪装**，等待结果。
4. 打开并使用 Galaxy Store、Samsung Members、TikTok 等目标应用。
5. 用完后回到本应用，或直接点击常驻通知中的**立即恢复**。
6. 确认移动服务恢复；如果 IMS 仍未注册，在系统设置中关闭再开启该 SIM，或重启手机。

关闭某一层的开关，只表示“下一次应用时不写这一层”，**不会移除已经生效的层**。必须点击恢复。检测到当前 SIM 正在伪装时，底部主按钮会自动变成**结束并恢复**。

## 两层分别做什么

| 界面名称 | Android 机制 | 常见读取方 | 主要代价 |
|---|---|---|---|
| **SIM 运营商 / Network** | `ITelephony.setCarrierTestOverride` | Galaxy Store 和三星应用 | 假 MCC/MNC 可能让下一次 IMS 注册使用错误运营商域名并失败 |
| **应用国家/地区 / Country** | `CarrierConfigManager.overrideConfig` | TikTok 及读取 SIM ISO 的应用 | 会重载 CarrierConfig，可能触发暴露假 Network 身份的 IMS 重连 |

Network 层写入 MCC/MNC、测试 IMSI、SPN 和 PNN；ICCID、GID、APN 和运营商权限规则保持 `null`。Country 层写入 `sim_country_iso_override_string`，并可选修改订阅显示名称。

应用地区不一定只由 SIM 决定。账号地区、IP、CSC、应用版本、服务端实验和缓存都可能参与判断。每次开启和恢复后，本工具会强制停止所选目标应用，使它们下次冷启动时重新读取框架状态。

## 通话、IMS 与恢复

实机结论不是简单的“两个开关一起开就断话”，而是：

- 假 **Network MCC/MNC 是 IMS 重注册失败的潜在根因**；
- **Country 的 CarrierConfig 刷新是常见触发器**，会使旧 IMS 会话重建；
- Network-only 可能暂时保留原来的中国运营商 IMS 会话，看起来一切正常；但丢信号、飞行模式、SIM 开关、UICC/CarrierConfig 刷新或其他 IMS 重连仍可能在之后触发失败；
- 应用后观察 8 秒的 `isImsRegistered(subId)` 只能说明“当前仍注册”，不能保证下一次重连。

在测试的中国联通 SIM 上，三星 IMS 仍使用中国联通配置和 APN，却根据伪造的 EE MCC/MNC 生成 `ims.mnc030.mcc234.3gppnetwork.org`，随后收到 SIP `403 Forbidden`。先恢复真实 Network 身份，再切换 UICC 应用，IMS 才恢复注册。

当前恢复流程因此会：

1. 先恢复仍在生效的 Network；
2. 回暖真实国家缓存，并等待最终 CarrierConfig 清除完成；
3. 只要假 Network 可能仍在，就绝不主动切换 UICC；
4. 恢复修改前保存的订阅显示名称；
5. 仅在 Network 已确认真实且 IMS 掉线时切换 UICC；
6. 等待 IMS 重新注册，无法确认时显示警告，而不是宣称成功。

完整实验见 [IMS 调查记录](docs/ims-investigation.md)。

## 恢复安全措施

- 第一次修改任意一层前，同时保存真实 MCC/MNC、运营商名、国家 ISO 和显示名称，避免“先 Network、后 Country”把伪造国家保存成原始值。
- 特权写入前同步保存 `PENDING` 日志。即使进程在 Android 已修改、长报告尚未返回时被杀，重新打开后仍会优先提示恢复。
- 固件允许时，用 SIM/card 的单向指纹绑定快照，避免 Android 重用 `subId` 后把旧卡信息写到新卡。原始 ICCID 不会离开 shell 服务。
- 按订阅记录一次临时会话使用过的目标应用；恢复时停止“应用时集合 ∪ 当前集合”，中途修改选择也不会遗漏 Galaxy Store。
- 核心覆盖重启即失效。运营商显示名称可能产生更持久的订阅数据库副作用，因此仍应执行一次正常恢复。

## 要求

- Android 10（API 29）或更高；
- 主要面向较新的三星固件，其他 Android 属于实验兼容；
- Shizuku 13+，以 shell 或 root 运行并完成授权；
- 有效订阅；Network 层还要求 SIM 为 `READY`。

应用只会在第一次真正进入伪装状态时请求通知权限。拒绝不会阻止功能，只会失去常驻提醒和一键恢复入口。

## 多语言

界面提供英语、简体中文、繁体中文、日语、韩语、法语、德语、西班牙语、巴西葡萄牙语、俄语、土耳其语、阿拉伯语、印度尼西亚语、泰语和越南语。Android 13 及以上可在系统“应用语言”中单独选择，右上角菜单会直接打开该页面；旧版 Android 跟随系统语言。

底层 Binder/Instrumentation 技术报告固定使用英语，以保证机器标记和问题报告不会随界面语言改变。

## 默认目标应用

| 包名 | 应用 |
|---|---|
| `com.sec.android.app.samsungapps` | Galaxy Store |
| `com.samsung.android.voc` | Samsung Members |
| `com.zhiliaoapp.musically` | TikTok |

目标列表可以自行修改。面板支持强制停止、清缓存/清数据和重新启动。清除 **Data** 会退出账号，并删除下载、草稿与本地设置。测试固件上的 cache-only 清除可能超时或无效果，报告会如实显示。

## 构建与测试

正式签名 APK 请从 [GitHub Releases](https://github.com/Ritel-T/SamsungRegionOverride/releases) 下载。
每个 Release 都会公布 APK SHA-256 与签名证书 SHA-256，安装前应同时核对。

需要 JDK 17 或更高版本：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

调试 APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

维护者可按 [docs/RELEASING.md](docs/RELEASING.md) 中的环境变量流程构建正式签名版本；签名材料不得进入仓库。

## 隐私与边界

应用没有 Internet 权限、遥测或账号系统，所有操作都在本机通过 Shizuku 完成。它不会提供运营商权益、付费内容或网络接入。请只在自己控制的设备和账号上使用，并遵守目标服务条款及当地法律。

本项目与 Samsung、Galaxy Store、Samsung Members、TikTok/ByteDance、Google 和 Shizuku 均无隶属、合作或背书关系。

## 已知限制

- 目前只有 SM-S938B / Android 16 / One UI 8.5 完成端到端验证；
- 当前界面最多展示两个活动订阅；
- 预设只是便捷数据，不是实时运营商数据库；只有 EE / `23430` / `gb` 在参考设备上完整应用并恢复过；
- 某些固件不允许读取稳定 card 标识，新快照会被标记为“身份未验证”；如果之后突然可以读取，本工具不会自动把它绑定到旧快照，需先重启清除核心覆盖，再清除本应用存储后开始新会话；
- 即使两层都生效，部分应用仍可能继续使用账号、IP 或旧缓存地区。

## 许可证

[MIT](LICENSE)。内置的 Shizuku 库继续使用其 Apache-2.0 许可证，详见
[第三方许可说明](THIRD_PARTY_NOTICES.md)。
