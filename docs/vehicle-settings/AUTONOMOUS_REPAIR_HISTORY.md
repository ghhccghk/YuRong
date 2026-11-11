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

### A10提交核对 / A11起点

- A10提交58a83fff5da182b396ef7dc50585b936771444c2，6文件；tracked/index干净，原有12项untracked保持，无外部提交。

## A11（独立复审及验证完成）

- ID20/86：三档音量、亮度UNKNOWN修复、严格目标/稳定ID/直接命令校验、无效观察与在途保护；亮度启用门控；共8代码/测试文件及3持续记录文件。未修改协议/编码/网络。
- 精确过滤：VolumeBrightnessSettingsSafetyTest12、TenthRoundAlarmSensitivityTest10、EleventhRoundSliderIntegrationTest10、CarSettingsControllerTest17、VehicleSettingGoldenFrameTest3，共52/52。命令`:shared:jvmTest --tests <上述五个完整类名> --offline --console=plain`，首次退出1，修正夹具后退出0。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；`git diff --check`退出0。缓存均C:\Users\DozenOregano696\.gradle。
- 独立复审重新检查UI、Controller、实际Writer、最终命令；目标值不进入观察、同VIN/序号/缓存隔离保持。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无运行/车辆验证。待精确提交。

### A11提交核对 / A12起点

- A11提交efdc4267ff80bfe0849bc225836dbd655dcca303，11文件；tracked/index干净，原有12项untracked保持，无外部提交。

## A12（独立复审及验证完成）

- 17/18：原页独立开关及1/2/3档；撤销仅DTO出现的resistanceOn猜测读写；严格目标/稳定ID/命令范围；组合模式要求开关有效LIVE回读；高档确认到Controller；不改协议、编码、网络或原始TTP。
- 本批7个源/测试文件与3个持续记录文件。EnergyRecoverySettingsSafetyTest11、CarSettingsConfIdMappingTest4、CarSettingsControllerTest17、VehicleSettingGoldenFrameTest3、CloudConfiguredValueRefreshTest.existingCloudBooleanMappingsDisplayBothExplicitStatesWithoutLiveTtp1，共36/36；精确过滤`:shared:jvmTest --tests <上述完整类名/方法过滤> --offline --console=plain`退出0。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；`git diff --check`退出0。缓存C:\Users\DozenOregano696\.gradle。
- 独立重走UI→Controller→CloudWriter BLE分流→MappedWriter→forItem/forConfigId→最终命令；未知/小数/越界无取整钳位、最高档普通保存拒绝、取消不发送、草稿及VIN/序号/缓存/在途刷新隔离，接受与回读分离。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；双主题只静态复核，无RUNTIME_VERIFIED/VEHICLE_VERIFIED。待精确提交。

### A12提交核对 / A13起点

- A12提交26c3150b8bcc6986a78a1c79fe785d9ef6fcadb6，10文件；tracked/index干净，原有12项untracked保持，无外部提交。

## A13（独立复审及验证完成）

- 13/16/40：移除13未经页面绑定证实的alarmSoundEnable云端读写；13已有BLE62/63，严格UNKNOWN/稳定ID；原TTP字段缺失受保护解析，保留读链路BLOCKED。16/40独立云端目标在Controller/Builder/Writer均校验；40关闭参数从错误0修正为Smali证实7，valueJson同步。只修改对应设置与隔离测试。
- 12项CloudSwitchSettingsSafetyTest覆盖真实Writer分流、请求字段、UNKNOWN/非法、来源、失败/取消、草稿、VIN及刷新代次。加Controller17、Golden3、CloudConfiguredValueRefresh单方法1、Phase4.cloudToggleTypesMatchP4单方法1，共34/34。首次退出1（1失败：新测试错误假设允许同VIN并发刷新），改为断言去重并测试A→B→A旧请求后退出0；没有降低生产保护。
- 独立复审UI→Controller→实际Writer→Builder/命令：16/40没有BLE绑定/车辆回读，不把结果确认充当车辆值；13无读值时UI保持UNKNOWN禁用。没有修改协议、编码、网络架构及原始解析。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；`git diff --check`退出0。精确过滤使用上述五个完整类名/方法；缓存均C:\Users\DozenOregano696\.gradle。
- STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；无RUNTIME_VERIFIED/VEHICLE_VERIFIED。13底层WRITE_ONLY、读取BLOCKED；16/40云端必要链路COMPLETE，不代表车辆已确认。待精确提交。


### A13提交核对 / A14起点

- A13提交041f0b126af4940b6e677533cd3d214ec1958381，11文件；tracked/index干净，原有12项untracked保持，无外部提交。

## A14（独立复审及验证完成）

- ID42缺少原页资格门控：补同VIN GET状态→cal2→公式/提示解释→Controller→本次资格Item→实际Writer→A4/A5；UNKNOWN不能开启，非空提示仅说明，关闭独立。严格TTP0/1、无效live撤销、在途刷新及VIN/代次保护；不改变协议、编码、原始TTP、通用网络或其他设置项。
- 新RemainingMileageSettingsSafetyTest13项：公式分支、非法/缺失、请求格式、失败/取消、同VIN/新鲜度、草稿、旧VIN资格、直接Writer/Mapper绕过、资格读取期间观察不能确认后续写入。加Controller17、Golden3，共33/33，精确过滤退出0。
- 首次过滤命令编译退出1（误用了未引入JSON库，已改复用已有JsonSupport）；第二次31项失败4项（新测试错误假设目录只有一个项目、误把乐观display当vehicleValue），更正为按稳定ID选取及分别断言submitted/vehicle后31/31；补两项边界后33/33。没有放宽校验或删除断言。
- 命令：`:shared:jvmTest --tests top.ghhccghk.yadea.carsetting.RemainingMileageSettingsSafetyTest --tests top.ghhccghk.yadea.carsetting.CarSettingsControllerTest --tests top.ghhccghk.yadea.carsetting.VehicleSettingGoldenFrameTest --offline --console=plain`。全部Fake/内存，不调用真实云端或车辆。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；`git diff --check`退出0。缓存C:\Users\DozenOregano696\.gradle。
- 独立重走原页→VM/jp6回调→Gateway→Controller→实际Writer→命令；资格在等待期间的新观察不算写入后确认，关闭无需资格，确认提示不发送命令。UI只复用AppAlertDialog，无主题/平台改动。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；CONDITIONAL，无RUNTIME_VERIFIED/VEHICLE_VERIFIED。待精确提交。


### A14提交核对 / A15起点

- A14提交b0c065012d913005fd3c296a8ef2caf230af398f，9文件；tracked/index干净，12原有untracked保持，无外部提交。

## A15（独立复审及验证完成）

- 2边撑感应：追清原页按W0(1021/29)/39/F1选择10或AC；当前无可靠车型快照/AC帧时阻止不确定关闭。无39描述项或有效LIVE lock=0时证明独立10，开启0F不变。严格已知值/稳定ID/Writer同VIN/临时许可/观察失效；2/7/39开关在途互斥，不改共用落锁参数。
- 3携出助力：证实pushModeStatus读绑定，但原始解析缺失受保护，读取BLOCKED。现有11/12独立写入补严格UNKNOWN/稳定ID保护，UI仍UNKNOWN禁用。
- SideBraceSettingsSafetyTest12 + VehicleSettingsSecondPhaseTest26 + CarSettingsControllerTest17 + VehicleSettingGoldenFrameTest3，共58/58首次退出0；实际命令`:shared:jvmTest --tests top.ghhccghk.yadea.carsetting.SideBraceSettingsSafetyTest --tests top.ghhccghk.yadea.carsetting.VehicleSettingsSecondPhaseTest --tests top.ghhccghk.yadea.carsetting.CarSettingsControllerTest --tests top.ghhccghk.yadea.carsetting.VehicleSettingGoldenFrameTest --offline --console=plain`。Fake/内存隔离，不调用真实云端或车辆。
- JVM/JS/Wasm/测试源码编译命令`:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0。
- `:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；`git diff --check`退出0。全部缓存C:\Users\DozenOregano696\.gradle。
- 独立重走Binding→VM字段赋值→原页→Controller→实际Writer/Mapper→已有编码；确认H6来自isPrimaryUser（VM3442–3462），未以连接/isAuth猜角色。没有改原始TTP、帧、协议、网络或其他设置映射；共享落锁仅增加必要写互斥。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；2 PARTIAL/不确定关闭BLOCKED，3底层WRITE_ONLY/读取BLOCKED。无运行/车辆验证，待精确提交。

### A15提交核对 / A16起点

- A15提交61a9e9ab8039bd4619a10c39cd9886e21d4a80ee，9文件；tracked/index干净，原有12项untracked不变，无外部提交。

## A16（独立复审及验证完成）

- ID75：补真实iotRemoveAlarm TTP回填、严格0/1及缓存/live隔离；删除无原页读绑定的configuredValue猜测。P4/s20实际仅证实整数1，未知0关闭全边界阻止；FA帧本任务保护不动。接受与车辆回读分离。
- ID76：同VIN显式云端在线检查、原页关闭确认、原param-setting精确构建、专用写入receipt及返回ID轮询；缺本次ID不猜confId。直接Controller、Writer、Gateway入口均校验；取消/失败/VIN切换不伪装成功，online许可不进入观察或配置。
- 首次过滤61/61退出0；补取消、旧VIN轮询、原始Gateway负例及严格75成功响应检查后64/64退出0。TrackerSettingsSafetyTest17、CloudConfiguredValueRefreshTest30、CarSettingsControllerTest17。实际命令：`:shared:jvmTest --tests top.ghhccghk.yadea.carsetting.TrackerSettingsSafetyTest --tests top.ghhccghk.yadea.carsetting.CloudConfiguredValueRefreshTest --tests top.ghhccghk.yadea.carsetting.CarSettingsControllerTest --offline --console=plain`。Fake/内存隔离，无真实网络/车辆请求。
- `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain`退出0；`:androidApp:assembleDebug :desktopApp:build --offline --console=plain`退出0；缓存C:\Users\DozenOregano696\.gradle。
- 独立复审UI→原Binding/VM→Controller→实际Writer→Gateway→结果/回读；没有扩大其他设置映射、帧、原始TTP、网络签名或平台架构。STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED；75 PARTIAL/关闭及FA帧BLOCKED，76 CONDITIONAL。未运行App、无VEHICLE_VERIFIED。待精确提交。
