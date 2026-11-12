# 自主修复任务队列

起点：329effd69e72a4cdf60d3d910dccde2ad1bce247，master，2026-11-12。

## 正在处理

- 暂无。A27已完成并提交；从ID52校准及其他尚未逐项复核的功能继续。

## 待处理（候选，须先证据复核）

- 雷达告警模式/音量：`COMMAND_RADAR_ALARM`/0x12F的完整允许值、UI边界及可信回填尚未证实；补齐Smali资源/绑定或其他可靠证据前不得接入。
- 回归维护补充：EighthRoundValueStateTest、SixthRoundActivityBatchTest仍有音量3..7旧夹具，后续专项按三档Smali修正；不得放宽生产范围。
- 回归维护：Phase4CarSettingsTest含旧62索引/72方向/10不完整快照等过时断言，最终专项修正夹具；不得放宽生产校验。
- 其他全部项：按功能地图逐项核对读取、写入、门控及独立流程；发现安全问题优先插队。
- 基线4/58/61/55/92、5/7/11/39、9/82：检查当前代码并回归，避免重复历史修复。

## 证据限制候选

- 42：A14已追清jp6.h/i→cal2→VM.x→k5。资格响应缺失/非法时阻止开启；未运行App或实车验证。
- 13：原页TTP alarmSoundSwitchAfterStatus已有Smali证据，但KMP原始TTP模型/解析缺失；本任务禁止修改原始解析，读链路BLOCKED，保持UNKNOWN。低层写入WRITE_ONLY；UI无已知值时继续禁用，不猜测DTO字段。
- 3：Binding.H5→VM.w0→TTP pushModeStatus；KMP原始解析缺失，读取BLOCKED；现有11/12写入经严格UNKNOWN/稳定ID保护，UI继续UNKNOWN禁用。
- 2：原页关闭可能按车型1021/29+39状态选择AC。无对应车型来源/既有AC编码时阻止不确定关闭；39未下发或有效LIVE锁状态0时可以证明10安全。保持PARTIAL，不新增帧。
- 75：原页和内联均读iotRemoveAlarm；P4/s20对非null Boolean固定写tamperProofIotEnable=1，0关闭缺证据，已阻止。原FA帧受保护边界限制；不补帧、不将字段名当云端配置读取证据。
- 52校准、51事务关联、77排程、33模式、93当前值、1005特定名称、复杂独立页面：复核后逐项列出BLOCKED/PARTIAL原因；此处不是最终判定。

## 恢复规则

每批开始读本文件、地图、历史、HEAD和staged/unstaged/untracked；只从最近成功提交后继续。禁止清理或暂存用户原有12项未跟踪内容。未通过测试、构建、自审及精确diff时不提交；不调用真实网络/车辆。

## 已完成

- A27：ID1一键开机距离档位写入安全，精确提交记录在`AUTONOMOUS_REPAIR_HISTORY.md`；普通档位链路已复核，用户写入结果不冒充车辆确认；原有12组untracked保持。

- A26：ID24/85联系人流程跨VIN状态隔离，提交`e305f2bdc19f90f8a748f3eb709d30779df38e67`；专属Fake测试9/9（含Controller 8项、Gateway 1项）、共享JVM/JS/Wasm/测试源码编译、Android/Desktop构建及diff检查退出0。非Android Gateway注入/服务端字段回读确认仍PARTIAL；原有12项untracked保持。

- A25：71仪表同步配置开关，提交`3bf4645616b18f93e4db058e94d2797796d57597`；35/35 Fake过滤测试、JVM/JS/Wasm/测试源码编译、Android/Desktop构建及`git diff --check`退出0。云端配置写入/复读已完成，仪表执行确认和独立立即同步流程仍PARTIAL；提交前保留12项既有untracked内容。

- A24：69/70/78雷达开关，提交`d4dbcf46e402b7e4d056fa5c46fae7e93adec2f5`；7项Fake过滤测试及跨平台编译、Android/Desktop构建退出0；`COMMAND_RADAR_ALARM`/0x12F仍BLOCKED。原有12项untracked保持。

- A23：64/89自适应推行写入安全，提交`870cfb186c6383fd292d779754843e821eb9a35c`；55项过滤测试及全部离线构建退出0，原有未跟踪内容保持。

- A22：77自动休眠排程，提交c85cce4d6490dcde83e4278decd9063295e223bf；89项过滤测试及全部离线构建退出0，原有12项untracked保持。

- A21：77立即休眠蓝牙联动，提交6b3ed36dd7fa02fd34eb992724056e076182b3de；75项过滤测试及全部离线构建退出0，原有12项untracked保持。

- A20：77立即休眠云端流程，提交b4d33db6169d4bc247083daa7578be413b1239d0；65过滤测试及全部离线构建退出0，原有12项untracked保持。

- A19：93，提交1f0cd06464fff643c9e65bca4da589910dfeee22；27过滤测试及全部离线构建退出0，原有12项untracked保持。

- A18：51，提交fd376e3f7795bc58420c87b9213148b7c24b2c43；33过滤测试及全部离线构建退出0，原有12项untracked保持。

- A17：33，提交812a6c8d2ee1667b80f4f500a6e26b50cf303715；32过滤测试及全部离线构建退出0，原有12项untracked保持。

- A16：75/76，提交6e5038c5c4207cb8939facbc86c97b42e52fd4ff；64过滤测试及全部离线构建退出0，原有12项untracked保持。

- A15：2边撑感应/3携出助力，提交61a9e9ab8039bd4619a10c39cd9886e21d4a80ee；58过滤测试及全部构建退出0，原有12项untracked保持。

- A01：47/48/60/62安全及映射，提交ef55352c8503e677ba4a3e3c2fb431b348c69457；151过滤测试和所有构建退出0。

- A02：15/59/21/81安全及时间映射，提交68c7baaccbcfc04c72f2bddfbfc5cc5da860536d；163过滤测试和所有构建退出0。

- A03：27/31/50/54 Choice安全，提交2ef8896158e5bcdcaf75ec8fe4b85e3bd84c69d9；41过滤测试/所有构建退出0。

- A04：10围栏复合云端安全，提交0e587d40374dd90b36f7d457b43bed498d9cf87f；53过滤测试/所有构建退出0。

- A05：73氛围灯安全及回填，提交6068cf1b61434aed6de14177fbcedf9a981070c4；37过滤测试/所有构建退出0。

- A06：19/26/36/53开关安全，提交13e06e7d963a59221d03d040fc4540ec5004194b；59过滤测试/所有构建退出0。

- A07：37/38/46/49，提交a2e8f9088853edeab9ca8637e37994e9c97ab03e；30过滤测试及全部构建退出0。

- A08：63/68/72/79，提交e62b59ffe98d7d97c1c1e5ef6ab9ca0244c2ec39；31过滤测试及全部构建退出0。

- A09：80/84/90/94，提交204f9664cb77cda3e9a6b546807b5017ad490010；32过滤测试及全部构建退出0。

- A10：32/43/44/45，提交58a83fff5da182b396ef7dc50585b936771444c2；33过滤测试及全部构建退出0。

- A11：20/86，提交efdc4267ff80bfe0849bc225836dbd655dcca303；52过滤测试及全部构建退出0。

- A12：17/18，提交26c3150b8bcc6986a78a1c79fe785d9ef6fcadb6；36过滤测试及全部构建退出0。

- A13：13/16/40，提交041f0b126af4940b6e677533cd3d214ec1958381；34过滤测试及全部构建退出0。

- A14：42，提交b0c065012d913005fd3c296a8ef2caf230af398f；33过滤测试及全部构建退出0。
