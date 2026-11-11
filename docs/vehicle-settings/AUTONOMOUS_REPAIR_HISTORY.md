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

### A03提交核对 / A04起点

- A03提交2ef8896158e5bcdcaf75ec8fe4b85e3bd84c69d9，8文件；提交后tracked/index干净，12既有untracked未变，无外部提交。
- A04以此HEAD复核ID10，发现范围及四字段伴随提交缺口，正在局部修复。

## A04（独立复审及验证完成）

- ID10，起始2ef8896158e5bcdcaf75ec8fe4b85e3bd84c69d9。
- 修正200..2000米整数；新增不可变GeofenceConfiguration承载四字段同VIN快照、读取两个原始圆心字段；Controller/Builder/Writer均拒绝缺失兄弟，四字段组合保留原App语义。Cloud-only，任何split ID均无BLE回退。
- 后续兄弟提交必须刷新到包含前次接受目标的新配置；可保留/准备草稿，有明确刷新提示。在途保护、取消/VIN/请求代次沿用原架构。
- 修改Controller/Mapper/Commands/GeofenceSettings、GeofenceSettingsSafetyTest、CloudConfiguredValueRefreshTest、CloudCarSettingsApiSemanticsTest及3任务文档。
- 初轮41失败4(Fake未模拟云端结果)，第二轮53失败1(旧测试没有四字段快照)；修正夹具并增强来源/请求断言，最终53/53退出0；复审改善草稿提示后再次53/53退出0。
- 最后一次JVM/JS/Wasm/测试源码编译退出0，Android Debug/Desktop构建退出0，diff检查退出0。
- 独立重走UI→Controller→完整性/兄弟刷新→CloudWriter→Builder→原Gateway路径；未改签名、URL、通用HTTP、帧、主题或其他设置。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；字段齐全才CONDITIONAL可写，无RUNTIME_VERIFIED/VEHICLE_VERIFIED。待精确提交。

### A04提交核对 / A05起点

- A04提交0e587d40374dd90b36f7d457b43bed498d9cf87f，10文件；提交后tracked/index干净，12既有untracked未变，无外部提交。
- A05以此HEAD复核73氛围灯；发现默认RGB/模式钳位直写、TTP配置未消费、模式依赖组合错误及缺失回读比较。

## A05（独立复审及验证完成）

- 起始HEAD：0e587d40374dd90b36f7d457b43bed498d9cf87f。
- Smali及修复逻辑见功能地图；未修改原始TTP解析、帧、命令ID、网络或主题。

- 新AtmosphereSettingsSafetyTest 11/11；连同FourthRoundVehicleSettingsTest 6、CarSettingsControllerTest17、VehicleSettingGoldenFrameTest3，总37/37退出0。
- 前两次过滤退出1，分别3/1失败：旧夹具依赖默认值/直接复合构造；旧黄金帧断言01与tj.c:704–731→uj.c:188–216保留高位证据不符，改为FD及对应校验和63。保留原帧编码，未降低生产检查。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0。
- `:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0。git diff --check退出0。
- 从Sheet→草稿/Controller→Writer→forItem→不可变命令→既有Encoder重新复审，未发现目标外变化。UNKNOWN不补齐、VIN/旧序号/缓存与草稿保持、失败/取消及接受与回读分离通过Fake测试。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无App运行/车辆验证。完整车辆配置条件下CONDITIONAL，未知配置保守禁用，PARTIAL。待精确提交。

### A05提交核对 / A06起点

- A05提交6068cf1b61434aed6de14177fbcedf9a981070c4，10文件。提交后tracked/index干净，12既有untracked不变，无外部提交。
- A06以此HEAD审查19/26/36/53，先完成只读Smali与KMP链路复核。

## A06（独立复审及验证完成）

- 四项开关UNKNOWN直接写入/伪造split ID、无效TTP冒充当前值；53把DTO-only字段当成边撑配置读写；36缺原App关闭二次确认。
- 局部修复SimpleToggleSettings、Controller、Mapper/Builder、命令入口、UI确认及Fake测试，不改协议、帧或网络基础设施。

- SimpleToggleSettingsSafetyTest9、CloudConfiguredValueRefreshTest30、CarSettingsControllerTest17、VehicleSettingGoldenFrameTest3，共59/59，过滤命令退出0。
- JVM/JS/Wasm/测试源码编译命令退出0；Android Debug/Desktop构建命令退出0；git diff --check退出0。所有Gradle命令均--offline --console=plain。
- 独立复审：UI→Controller→命令/Writer至既有编码；53猜测云端路径已关闭，实际BLE调用保持；无目标外修改。36关闭确认仅静态核对，未运行App。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。四项必要BLE链路静态/Fake闭环，云端能力未伪造。待精确提交。

### A06提交核对 / A07起点

- A06提交13e06e7d963a59221d03d040fc4540ec5004194b，10文件；tracked/index干净，12既有untracked未变，无外部提交。
- A07以此HEAD审查37/38/46/49，扩展同一严格开关边界；不是重复实现已有UI/命令。

## A07（独立复审及验证完成）

- 新四项UNKNOWN/稳定ID/无效观察保护，TCS直接参数严格0..7；沿用原有TCS/ABS二次确认及BLE供电门控。
- SimpleToggleSettingsSafetyTest10、CarSettingsControllerTest17、VehicleSettingGoldenFrameTest3，共30/30，过滤命令退出0。
- JVM/JS/Wasm/测试源码编译退出0；Android Debug/Desktop构建退出0；git diff --check退出0。Gradle均离线执行。
- 独立复审UI→Controller→Writer→forItem/forConfigId→命令构造，未修改帧、协议及其他设置业务。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行或车辆验证。待精确提交。

### A07提交核对 / A08起点

- A07提交a2e8f9088853edeab9ca8637e37994e9c97ab03e，7文件；tracked/index干净，12既有untracked保持，无外部提交。
- A08以此HEAD只读复核63/68/72/79后实施：发现68写入反向、72读取/写入反向；四项存在UNKNOWN和无效观察保护缺口。

## A08（独立复审及验证完成）

- 只调整四项严格开关边界、命令参数方向及最终参数合法性；不修改编码/协议。
- SimpleToggleSettingsSafetyTest11+Controller17+Golden3=31/31，精确过滤命令退出0；JVM/JS/Wasm/测试源码编译退出0；Android Debug/Desktop构建退出0；git diff --check退出0。所有Gradle命令--offline --console=plain。
- 独立复审重新从UI→Controller→CloudWriter BLE分流→MappedWriter→映射及最终命令边界；0/1域、来源、草稿、VIN/序号、接受与确认分离均由Fake覆盖，无其他设置业务变化。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/车辆验证。待精确提交。

### A08提交核对 / A09起点

- A08提交e62b59ffe98d7d97c1c1e5ef6ab9ca0244c2ec39，7文件；tracked/index干净，12既有untracked未变，无外部提交。
## A09（独立复审及验证完成）

- 只读复核80/84/90/94后，修正80/84/94开启方向并补四项严格开关保护。

- A09 SimpleToggle12+Controller17+Golden3=32/32，过滤命令退出0；JVM/JS/Wasm/测试源码编译退出0；Android Debug/Desktop构建退出0；diff检查退出0，所有Gradle离线。
- 独立重走UI→Controller→CloudWriter→MappedWriter→forItem/forConfigId→最终命令，目标四项之外无生产变化；STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED。无运行/车辆验证，待精确提交。

### A09提交核对 / A10起点

- A09提交204f9664cb77cda3e9a6b546807b5017ad490010，7文件；tracked/index干净，12既有untracked保持，无外部提交。

## A10（独立复审及验证完成）

- 32/43/44/45：严格UNKNOWN、稳定ID、观察失效及在途保护；45修正回填字段。协议和帧不变。

- SimpleToggle13+Controller17+Golden3=33/33，过滤命令退出0；JVM/JS/Wasm/测试源码编译退出0；Android Debug/Desktop构建退出0；diff检查退出0，所有Gradle离线。
- 独立审查入口→Controller→两个Writer→映射/最终命令：四项之外生产行为不变；无默认未知值、旧观察/VIN污染或接受冒充确认。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/实车验证。待精确提交。
