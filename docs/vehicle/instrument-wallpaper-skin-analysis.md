# toupinNew.apk 仪表壁纸与皮肤逻辑分析

> 本文档基于 `toupinNew.apk`（包名 `com.example.bleconnectse`，SHA-256 `C133E45210464AB7DB0CB1E00A0E0B0B18B7C0D2C1043D20B65EF67FCF7F8DED`）的**静态反编译**结论。
> 未运行 APK，未连接车辆/云端，未抓包。协议字节细节（尤其 `0x21/0x23/0x24` 的兼容特例）建议结合抓包验证。
> 反编译产物目录：`../../jadx-toupinNew/`；补充单类输出：`../../jadx-single-skin.java`、`../../jadx-single-skin-generator.java`。

## 1. 总体链路

手机先连接仪表 WiFi，再通过 TCP 端口 `8880` 上传数据。支持两种网络模式：

| 模式 | 前置条件 | 目标地址 |
| --- | --- | --- |
| AP | `isApReady()` | `192.168.7.1` |
| P2P | 对端身份校验通过且 `p2pGroupOwnerIp != null` | P2P 群主 IP |

- 壁纸、皮肤、投屏三类会话**互斥**，同时只能进行一个。
- 入口统一在 `YadeaOtaClient`（`startInstrumentWallpaper` / `startInstrumentSkinImage$app` / `stopInstrumentSkin`）。
- 主入口：`com.example.bleconnectse.MainActivity`
  - 壁纸选图回调：`MainActivity.java:358`
  - 皮肤预设入口：`MainActivity.java:886`

## 2. 公共协议帧（WallpaperProtocol）

定义：`jadx-toupinNew/sources/com/example/bleconnectse/WallpaperProtocol.java:15`

- 请求协议 ID：`60 / 0x3C`
- 响应协议 ID：`76 / 0x4C`
- 外层帧（含校验）：

```
59 44 <protocol> <BE32 innerLen+3> <inner> <checksum> 4B 4A
```

- 内层帧：

```
<cmd 1B> <BE32 dataLen> <data>
```

| 命令 | 含义 |
| --- | --- |
| `0x10` | START |
| `0x11` | PARAM |
| `0x12` | DATA |
| `0x13` | END |
| `0x1F` | 心跳 |
| `0x20` | START 响应 |
| `0x21` | PARAM 响应 |
| `0x23` | END 响应（生效确认） |
| `0x24` | DATA 响应 |
| `0xF1` | 仪表拒绝 |

反编译代码包含对异常固件的长度/校验和兼容特例（主要涉及 `0x21`、`0x23`、`0x24`）。

## 3. 壁纸逻辑（单张即时转码上传）

- 入口：`YadeaOtaClient.java:985`（`startInstrumentWallpaper`）
- 任务主体：`YadeaOtaClient.java:1062`
- 核心实现：`InstrumentWallpaperClient.java:130`

### 3.1 流程

```text
读取 Uri
→ 原图最多 30 MiB
→ TCP 8880
→ START 获取仪表尺寸和格式
→ 缩放到仪表返回尺寸并转码
→ PARAM 确认长度
→ DATA 按 1434 字节分块
→ 全部 DATA 发完后等待一次 0x24
→ END
→ 0x23 确认生效
```

### 3.2 尺寸协商与格式

- START 响应 `0x20`：前 4 字节为宽、高（BE16），第 5 字节为格式。
- 尺寸范围：`1..4096`；未知格式回退为格式 0。
- 图片直接拉伸到仪表尺寸，不做裁剪。

| 格式值 | 含义 |
| --- | --- |
| `0` | NV21 JPEG |
| `1` | PNG |
| `2` | JPEG |
| `3` | Jieli RLE |

非 PNG 先铺白底，避免透明区域发黑。

### 3.3 图片转换

- JPEG/NV21 质量依次：`100, 95, ..., 5`；体积上限 `131072` 字节（128 KiB）。
- PNG 不受 128 KiB 限制。
- 整图使用 CRC16-Modbus。

### 3.4 DATA 分块

- 块大小：`1434` 字节。
- DATA 头 18 字节：

```
CRC16 2B + chunkLen BE16 + 0 BE32 + imageLen BE32 + offset BE32 + timestamp BE32 + chunk
```

### 3.5 确认语义

- 壁纸所有 DATA 发完后**只等待一次** `0x24`（非每包确认）；`data[0] == 0` 才接受。
- END 后 `0x23` 首字节满足 `(data[0] & 3) != 0` 才算生效。
- 不保存转换后的壁纸；成功时记录“仪表壁纸设置成功：文件名”。

## 4. 皮肤逻辑（模板驱动的 20 帧重绘）

皮肤并非单图直传，而是**用新图逐帧重绘整套仪表动画资源，再打包上传**。

- 任务入口：`YadeaOtaClient.java:1282`（`startInstrumentSkinImage$app`）
- 停止入口：`YadeaOtaClient.java:1518`（`stopInstrumentSkin`）
- 生成器：`jadx-single-skin-generator.java:204`（`InstrumentSkinGenerator`）
- 输出固定 `800×480`

### 4.1 三种预设

定义：`InstrumentSkinPreset.java:17`

| 预设 | 显示名 | 行为 |
| --- | --- | --- |
| `PortraitOnly` | 换小圈 | 保留参考背景，仅替换圆形小圈 |
| `BackgroundOnly` | 换背景 | 用户背景全屏绘制，保留一圈描边；**不保留原小圈内容** |
| `PortraitAndBackground` | 小圈+背景 | 先选小圈，再开背景选择器，两者都换 |

### 4.2 生成流程

```text
校验目标
→ 读取 assets/skin/captured_800x480.bin（参考包）
→ 读取小圈图片，最多 30 MiB
→ 组合模式再读取背景图片，最多 30 MiB
→ 以参考包 20 张图逐帧重绘
→ 构建并自校验皮肤包
→ 保存本地皮肤包
→ TCP 8880 上传
→ 仪表确认皮肤生效
```

### 4.3 逐帧重绘细节

- 原图解码最大约 `1600×1600`，使用二次幂采样，处理 EXIF 方向 2–8。
- 小圈中心：`(184*scale + dx, 240*scale + dy)`
- 小圈半径：`306*scale`
- 姿态表：

| 图片 | 姿态 |
| --- | --- |
| `FD1_01..09.jpg` | `shrink[0..8]` |
| `SX1_01..09.jpg` | `enlarge[0..8]` |
| `LEVEL_1.jpg` | `shrink[0]` |
| `LEVEL_2.jpg` | `shrink[8]` |

- `PortraitOnly`：保留参考背景，用新图替换圆形小圈。
- `BackgroundOnly`：用户背景全屏绘制，保留一圈描边；不保留原小圈内容。
- `PortraitAndBackground`：新背景 + 新小圈。
- 小圈图和背景均 center-crop。
- 每轮重新生成全部 20 张图，JPEG 质量依次：`88, 75, 60, 45, 30, 20, 10`。
- 目标包长上限：`332496` 字节；全部质量仍超限时提示“图片细节过多……”。
- 支持取消与线程中断。

### 4.4 本地保存

- 保存路径：`<externalFilesDir>/skins/generated_SKINPACKRES`
- 外部目录为空时回退到 `context.getFilesDir()`。
- 保存发生在连接 TCP 之前；上传失败时本地生成包仍可能保留。

### 4.5 皮肤包格式

实现：`InstrumentSkinPack.java:37`

包内容：

```
FD1_01.jpg ... FD1_09.jpg
LEVEL_1.jpg
LEVEL_2.jpg
SX1_01.jpg ... SX1_09.jpg
version.ini
index.idx
skin（分区头）
```

`version.ini`：

```ini
[config]
file=YADEA_5ANIM_EGG.avi
version=1.58
crc=xxxxxx
```

每张图片的 CRC32 写入 `version.ini`。

包结构：

- 目录项固定 32 字节：

```
目录CRC16 LE16 + 资源CRC16 LE16 + offset LE32 + size LE32 + type + FF + last LE16 + 16B ASCII 名称
```

- 普通资源 `type=2`；`skin` 分区头 `type=3`。
- 目录项两个 CRC16 和资源内容都使用 CRC-CCITT（poly `0x1021`、初值 `0`）。仪表 TCP 报文中的 DATA 头才使用 CRC16-Modbus，两者不能混用。
- 数据按 16 字节对齐，填充 `FF`。
- 包总上限 `8 MiB`。
- `parse()` 校验：名称、顺序、CRC16、CRC32、偏移、长度、对齐、填充和尾部多余数据。
- 生成器完成后再次调用 `parse()` 自校验。

`index.idx` 由固定 TIDX 头和 21 个文件条目生成，包含 `version.ini`；`parse()` 会重新生成索引并逐字节比较。

## 5. 皮肤传输协议（InstrumentSkinTransfer）

实现：`InstrumentSkinTransfer.java:128`，复用壁纸协议 ID `60`。

- 分包大小：`32768` 字节。
- START payload：`80 00 F4 FF 00 00 05 9A`
- PARAM：`<BE32 packLength> F6 FF FF FF`
- END：`FD FF FF FF FF FF FF FF`

```text
HEARTBEAT + START
→ 0x20（须 8 字节，确认 800×480、格式 0）
→ PARAM
→ 0x21（须 8 字节，长度等于包长）
→ DATA*
→ 每个 DATA 都等待 0x24（非最后包须 0x04，最后包须 0x00）
→ END
→ 0x23（须 8 字节，且 (data[0] & 3) == 1）
```

- 每秒一次心跳；响应超时 15 秒；`0xF1` 表示仪表拒绝。
- DATA 头与壁纸相同，但 `packLen` 是整个皮肤包长度。
- 仅适配 `800×480`、格式 `0` 的仪表。
- 心跳载荷使用当前时间：`年%100 / 月 / 日 / 时 / 分 / 秒 / FF / FF`。

## 6. 停止与失败语义

| 会话 | 实现 | 行为 |
| --- | --- | --- |
| 壁纸停止 | `stopInstrumentWallpaper` | 设置停止标志、关闭活动 socket；文本“壁纸设置已停止”；清除会话标志 |
| 皮肤停止 | `stopInstrumentSkin` | 设置 `skinStopRequested`、关闭 socket；文本“皮肤任务已停止”；清除 `activeSkinSocket`/`skinSessionActive`，异步 `setSkinBusy(false)` |

- 皮肤任务过程中网络或目标车辆变化时重新校验目标并失败。
- 皮肤 UI 在 `skinBusy=true` 时忽略新的预设点击。

## 7. 关键代码索引

| 逻辑 | 文件与行号 |
| --- | --- |
| 壁纸选图回调 | `jadx-toupinNew/sources/com/example/bleconnectse/MainActivity.java:358` |
| 皮肤预设入口 | `MainActivity.java:886` |
| 皮肤预设枚举 | `InstrumentSkinPreset.java:17` |
| 壁纸入口 | `YadeaOtaClient.java:985` |
| 壁纸任务 | `YadeaOtaClient.java:1062` |
| 壁纸核心实现 | `InstrumentWallpaperClient.java:130` |
| 皮肤入口 | `YadeaOtaClient.java:1282` |
| 皮肤停止 | `YadeaOtaClient.java:1518` |
| 皮肤生成主任务 | `jadx-single-skin.java:1484` |
| 皮肤生成器 | `jadx-single-skin-generator.java:204` |
| 皮肤包格式 | `InstrumentSkinPack.java:37` |
| 皮肤传输协议 | `InstrumentSkinTransfer.java:128` |
| 公共协议帧 | `WallpaperProtocol.java:15` |

注：以上路径均为相对 `docs/vehicle/` 的路径；APK 本体位于项目根目录 `toupinNew.apk`。

## 8. 总结

- 壁纸：单张图片按仪表协商参数即时转码、分块上传。
- 皮肤：用新图按姿态表重绘 20 帧动画资源，打包成 `800×480` 皮肤包后上传。
- 两者共用 TCP `8880` 与协议 ID `60`，但确认粒度不同：
  - 壁纸仅在全部数据后等一次 `0x24`；
  - 皮肤每个数据包都等待 `0x24` 确认。

## 9. 当前仓库实现状态

- 协议帧、皮肤包生成/校验和 TCP 会话位于 `commonMain`。
- Android 与 Desktop 提供图片选择、位图重绘和 JPEG 编码；iOS、JS、Wasm 通过明确的能力状态禁用该功能。
- Android 支持壁纸格式 `0`（NV21 JPEG）、`1`（PNG）和 `2`（JPEG）。
- 格式 `3` 杰理 RLE 依赖原 App 的 arm64 原生 `libjl_uiresconv.so`，当前实现会明确拒绝，不会回退发送错误格式。
