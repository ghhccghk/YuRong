# 自主修复历史

## 任务起点

- 日期：2026-11-12。
- HEAD：329effd69e72a4cdf60d3d910dccde2ad1bce247，分支master。
- staged/unstaged：空；12项既有untracked保持原样（日志、APK、jadx、截图、用户文档）。未fetch、未切分支。
- 已有提交：759c05c第一阶段，720f06f低电量，329effd第二阶段。本任务不重复旧结论。
- 读取AGENTS.md、现有车辆设置文档、当前代码与Smali。full-read-write-audit-2026-11-11.md未找到。
- 后续成功批次见下方；不把任务前旧测试/构建结果计作本轮验证。

## A01（已提交）

- ID：47/48/60/62；原因：默认值与钳位可穿透直接写入入口。
- 起始HEAD：329effd69e72a4cdf60d3d910dccde2ad1bce247。
- 证据/修改/验证/自审：完成后填写。

### A01 验证记录

- 精确过滤BatterySettingsSafetyTest：17/17，退出0。
- 回归过滤BatterySettingsSafetyTest、CloudConfiguredValueRefreshTest、VehicleSettingsFirstPhaseTest、VehicleSettingsSecondPhaseTest、LowPowerReminderTest、VehicleSettingGoldenFrameTest、CarSettingWriterAccessTest、CarSettingsControllerTest：151/151，退出0。先前两次回归失败退出1，原因及修复见地图A01，不隐藏失败。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`：退出0。
- `:androidApp:assembleDebug :desktopApp:build --offline --console=plain`：退出0。Android构建完成，Desktop无测试源码（test NO-SOURCE），没有执行App。
- `git diff --check`：退出0。HEAD未变化，origin/master本地引用ahead20/behind0（未fetch）。
- 所有测试为内存Fake，无真实网络/BLE/车辆操作。没有启动App。

- A01独立自审通过，验证等级：STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行或实车验证。待精确提交；提交Hash在下一批起点记录（避免自引用）。

### A01 提交核对 / A02 起点

- A01提交：ef55352c8503e677ba4a3e3c2fb431b348c69457，10文件（4生产、3测试、3任务文档），提交退出0。提交后tracked/index干净，12项既有untracked原样。
- A02以此HEAD开始；已重读队列/地图及提交，未发现外部提交；目标15/59/21/81。

## A02（独立复审及验证完成）

- ID15/59/21/81，起始ef55352c8503e677ba4a3e3c2fb431b348c69457。
- TimerSettings、Controller、Mapper、命令最终构造、Cloud配置回归测试、新TimerSettingsSafetyTest和3任务文档；无协议、帧或网络修改。
- 范围/选项/UNKNOWN/DTO猜测读取及别名并发缺陷已修；Smali/APK资源证据见地图A02。
- 初次过滤退出1（新测试夹具缺values实参），修正后59/59退出0；最终9类163/163退出0，均Fake-only。
- JVM/JS/Wasm/测试源码编译命令退出0；Android Debug/Desktop构建命令退出0；git diff --check退出0。
- UI→Controller→forItem/forConfigId→Writer→最终命令全链独立复审通过，未发现目标外改动；VIN/旧序号/缓存/取消/失败/草稿保持及回读分离测试通过。
- 15原始0/1真实计时语义证据不足，保留UNKNOWN/PARTIAL。无App运行和车辆验证。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED。待精确提交，Hash在下一批起点记入。

### A02提交核对 / A03起点

- A02提交68c7baaccbcfc04c72f2bddfbfc5cc5da860536d，9文件；提交后tracked/index干净，12既有untracked未变，无外部提交。
- A03以此HEAD审查27/31/50/54；发现直接Mapper未知索引与钳位可写入，拟修复最终边界。

## A03（独立复审及验证完成）

- ID27/31/50/54，起始68c7baaccbcfc04c72f2bddfbfc5cc5da860536d。
- 四项Choice的未知/越界/伪造选项及split ID绕过校验；原始命令0x3F/0xC6范围保护；不改协议/帧/网络，完整证据见地图。
- 修改SimpleChoiceSettings、Controller、Mapper、Commands、新SimpleChoiceSettingsSafetyTest及3任务文档。
- 首轮退出1（新测试位置实参顺序错误），更正命名实参后9+12+17+3=41/41退出0。
- JVM/JS/Wasm/测试源码编译退出0；Android Debug/Desktop构建退出0；diff检查退出0。
- 从UI入口重新独立审查至最终命令构造，四项目标以外无业务变化。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/实车验证。待精确提交。
