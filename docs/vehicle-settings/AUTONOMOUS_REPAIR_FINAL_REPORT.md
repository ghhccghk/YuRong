# 车辆设置自主审计与修复最终报告

日期：2026-11-12。分支：master。没有真实云端、BLE、车辆操作，没有push。

## 结论与范围

A01–A61共61批记录，覆盖93个审计实体：83个数字confId（含001/002/003）、5个动态目录入口、5个无固定ID页面。SmartBoot外设配对和ConfigSetting重复组合页不计入设置总数。72个直接getSettingConfigId实现、70个唯一ID均已覆盖，3个动态getSettingConfigIdList入口另核。这是现有Smali/目录枚举范围，不承诺未知车型运行时动态资源的绝对全集。

当前证据和受保护边界内可定位、安全实现的缺口已分批处理；剩余项列出PARTIAL/BLOCKED/CONDITIONAL原因。**这不表示所有车辆设置已完全实现或实车验证。** 收尾还发现已提交的帧编码范围偏差，见专节，不能宣称任务全程符合该边界。

- 全集/字段/调用链：[AUTONOMOUS_REPAIR_MATRIX.md](AUTONOMOUS_REPAIR_MATRIX.md)。
- 逐批Smali证据、修改、验证：[AUTONOMOUS_REPAIR_HISTORY.md](AUTONOMOUS_REPAIR_HISTORY.md)。
- 阻塞原因与恢复条件：[AUTONOMOUS_REPAIR_QUEUE.md](AUTONOMOUS_REPAIR_QUEUE.md)。
- 指定full-read-write-audit-2026-11-11.md未找到，未用历史报告代替当前代码复核。

## Git

- 任务起始HEAD：`329effd69e72a4cdf60d3d910dccde2ad1bce247`。
- 最后生产代码HEAD：`f63bc5944592900f59eca48befdde84e3a409701`。
- 本报告前74个本地提交：43个fix、4个test、27个docs。61是批次记录数，不是61个功能修复提交。本收尾为第75个提交，仅4份自主审计文档。
- 本次恢复起点7092deb，续作生产改动仅A60的ID91保护及Fake断言；A61只更新资料。
- 期间历史提交按实际HEAD接续，没有回退。最后恢复至本报告未发现外部提交。当前相对本地upstream引用ahead46/behind0，未fetch验证远端最新状态。
- 最后代码提交后tracked/index干净。14项既有untracked（日志、APK、截图、JADX、用户文档等）保留；最终仅精确暂存4个文档路径。未删除用户文件、全量暂存、推送或重写历史。
- 本报告所在提交自己的Hash由最终交付提供，避免提交内自引用Hash。前置提交完整列表附后。

## 能力统计

| 顶层状态 | 数量 |
|---|---:|
| COMPLETE | 41 |
| PARTIAL | 35 |
| CONDITIONAL | 9 |
| BLOCKED | 7 |
| READ_ONLY | 0 |
| WRITE_ONLY | 0 |
| UI_ONLY | 0 |
| NOT_IMPLEMENTED | 0 |
| NOT_APPLICABLE | 1 |
| 合计 | 93 |

统计取矩阵每行最后一列首个状态；PARTIAL内的BLOCKED子流程不重复累计。COMPLETE只表示列明作用域的静态/Fake必要链路，不含运行或实车验证。纯云配置闭环不表示车辆已执行。READ_ONLY/WRITE_ONLY为0仅指整体实体，不否认模式/音量等子项只读或某底层命令仅写。

## 主要交付

- P0安全：复合字段完整性、未知兄弟字段保护、最终命令合法域、非法Choice/Slider拒绝、错误通道阻断、VIN及旧请求隔离、草稿保留、接受与车辆回读分离。
- 多组灯光、安全辅助、运动模式、音量/亮度、能量回收/推行逐项核实原App枚举与反向参数；无法证明的车型分支继续阻止。
- 独立流程：电子围栏快照、立即休眠与自动排程、座椅加热任务CRUD、联系人/密码/SOS、仪表信息同步。既有低电量统一Sheet重新审计回归，不重复实现。
- 读取：ID3/13缺失TTP字段、ID47功率原始值乘50、X1映射、速度来源隔离、座椅加热UNKNOWN保护。解析存在不等于运行确认。
- A58（4ec625a）：补MainDestinationContent→VehicleHubScreen漏传的联系人和密码Controller；内部必传参数防止再次遗漏。
- A59（7092deb）：ID71立即同步复用原POST刷新请求，独立操作状态；只在同VIN、可写、明确配置开启且无在途草稿时放行。接受不改变vehicleValue。
- A60（f63bc59）：Smali两方向均检查SOS四项联系人；补Controller关闭方向绕过，缺资料不写、不改变已有开启状态，完整资料允许两个方向。更正A50“关闭不受限”。

普通读取为selectBikeSettingByVin→Mapper→Controller/configuredValue→UI；车辆观察进入vehicleValue或cachedValue，保留VIN/来源/序号。提交为UI显式编辑→Controller完整性/门控/VIN复验→CloudWriter或MappedWriter→Builder或forItem→既有Gateway/编码/Writer→接受、轮询或新鲜回读。独立流程分别复用其Controller/Gateway，通用函数存在不能证明已接通。

ID55/92使用不可变已验证四字段payload，原生List或默认值不能构造CUSTOMIZE_SETTING。ID4/58的bikeSpeedMax和ID61的highestSpeedLimit只作配置，maximumSpeedCurrent只作车辆/缓存观察。draft、submitted、配置、缓存、车辆、writeState、confirmationState分别保留。

## 剩余问题

| 项目 | 当前缺口/限制 | 恢复前提 |
|---|---|---|
| 001/002/003、3/5/11/13/15、47/48、1005 | 静态/Fake链路存在，部分车型/入口/车辆结果未实测，整体保守PARTIAL | 当前车型响应、运行及回读证据，不重复实现已有功能 |
| 1/52/66 | 基础档位已有；独立标定、授权与结果持久化未闭环 | 完整校准事务，不新增猜测字段 |
| 2/7/39 | 1021/29及H6/F1等车型/父状态门控缺可信等价来源 | 同VIN车型数据和分支证据 |
| 8 | 报失/SMS已有，配置刷新非车辆确认 | 授权后运行与执行结果 |
| 10/17/18/42/73/76/77/86 | 快照、父开关LIVE、cal2资格、模式/在线等条件化能力 | 满足原App条件及必要车辆验证 |
| 24/25/85/91 | Android宿主已通；密码云端/BLE非原子，其他平台注入/HTTP/身份契约不全 | 正式平台与结果契约，不改通用网络冒充支持 |
| 33模式/音量 | 读取存在，缺既有写入编码 | 专项协议授权 |
| 41/88 | 云资源/轮询已有；缺资源字节、专用GATT握手/分片/设备应用协调 | 文件/传输契约及专项能力 |
| 51 | C1偏好存在；C0缺编码/触发，解析结果域与比较5冲突 | 结果证据和受保护编码授权 |
| 60/62 | 即时和62排程已处理，部分云配置无原页绑定，执行未确认 | 脱敏配置/原绑定及实际回读 |
| 71及special同步 | 71动作已通；special-only权限与71关联无据，无车辆关联事务 | 目录权限和仪表结果契约 |
| 72/80 | 原独立页与动态页参数反向，保留已证明动态路径 | 相同车型两入口抓包/运行证据 |
| 75 | 仅证实云端开启1，关闭0无据，FA帧受保护 | 关闭请求与专项协议证据 |
| 93 | 已阻止误走3E，正确order39及当前值缺失 | 独立控制协议授权 |
| special壁纸/皮肤 | 本地上传已有，缺原云目录/下载/应用事务及入口 | 资源/权限/结果契约 |
| special用户智能识别 | 个人身份授权/参数作用域与全车不同 | 身份与子项完整调用链 |
| special实验室/设备详情 | 分类/Info Tab已有，聚合条件和完整资料契约不等价 | 独立子流程与详情数据契约 |
| independent:ready_p | 已解析readyP；原发E4，但无可信目录/既有编码 | 目录与专项帧授权，129只是埋点 |
| independent:low_power_mode | 原独立POST，状态/退出/资格/结果未闭环 | 控制事务证据，不混入9/82 |
| independent:multifunction/start_light/temperature_unit | 原可见代码缺持久化/字段/写入证据，部分onCreate为native | 原始业务证据，不从文字推测 |
| 95 | 仓库要求隐藏 | NOT_APPLICABLE，不恢复 |

## 范围偏差与限制

累计diff确认：A27提交`d971f7a2e15c7430b23660959138704643da1c65`修改了`shared/src/commonMain/kotlin/top/ghhccghk/yadea/carsetting/VehicleSettingFrameEncoder.kt`的0x37分支，由coerceIn(0,7)改为严格类型/范围检查后编码。虽然意图是拒绝非法输入，仍违反本任务禁止修改帧编码的范围。收尾没有再次修改它、重写历史或擅自回滚；后续处置需独立授权。本任务边界合规性为PARTIAL。

ID51 C0试作已在A58前精确撤回，试作的16项测试不计入保留代码证据。TtpInfo保留的3/13读取和47乘50是有Smali依据的状态解码修改。没有改网络签名、Token、通用HTTP、OTA或投屏。

审计是本地静态阅读及Fake；没有抓包、原App/KMP运行、视觉交互、真实云端读写或车辆验证。低电量Slider资源边界未在本次收尾重新解包；沿用已实现链路，不把Smali“提交整型进度”误写成重新证明所有布局资源。

## 最终验证

Gradle使用既有`C:/Users/DozenOregano696/.gradle`，全部`--offline --console=plain`。未运行完整jvmTest。

| Fake测试类 | 通过 | failures/errors/skipped |
|---|---:|---|
| VehicleSettingsFirstPhaseTest | 24 | 0/0/0 |
| LowPowerReminderTest | 29 | 0/0/0 |
| SosEmergencyCallSafetyTest | 4 | 0/0/0 |
| vehicle.EmergencyContactValidationTest | 8 | 0/0/0 |
| PasswordFlowSafetyTest | 17 | 0/0/0 |
| InstrumentInfoSyncTest | 7 | 0/0/0 |
| CloudConfiguredValueRefreshTest | 37 | 0/0/0 |
| 合计 | 126 | 0/0/0 |

上述7个全限定类分别作为`--tests`传给`:shared:jvmTest --offline --console=plain`，最后一次统一执行退出0；未将重复回归累加。A60首次误写联系人测试包名而只执行4项，已核XML并用正确vehicle包重跑，详见历史。

实际过滤命令：

```powershell
$env:GRADLE_USER_HOME = "C:\Users\DozenOregano696\.gradle"
.\gradlew.bat :shared:jvmTest --tests top.ghhccghk.yadea.carsetting.VehicleSettingsFirstPhaseTest --tests top.ghhccghk.yadea.carsetting.LowPowerReminderTest --tests top.ghhccghk.yadea.carsetting.SosEmergencyCallSafetyTest --tests top.ghhccghk.yadea.vehicle.EmergencyContactValidationTest --tests top.ghhccghk.yadea.carsetting.PasswordFlowSafetyTest --tests top.ghhccghk.yadea.carsetting.InstrumentInfoSyncTest --tests top.ghhccghk.yadea.carsetting.CloudConfiguredValueRefreshTest --offline --console=plain
```

| 实际执行命令 | 退出码 | 结论 |
|---|---:|---|
| 7类精确过滤`:shared:jvmTest` | 0 | 126/126 |
| `:shared:compileKotlinJvm :shared:compileKotlinJs :shared:compileKotlinWasmJs :shared:compileTestKotlinJvm --offline --console=plain` | 0 | JVM/JS/Wasm/测试源码编译 |
| `:androidApp:assembleDebug :desktopApp:build --offline --console=plain` | 0 | Android Debug/Desktop构建 |
| `git diff --check` | 0 | 无空白错误 |

编译和构建最后执行于A60代码，A61只有文档。Windows未验证iOS；现有弃用/unchecked/expect-actual警告不当作新增修复。

按追加要求，最终本地提交后再执行`:androidApp:assembleDebug --offline --console=plain`；该次结果、APK绝对路径、大小和SHA-256由交付输出记录。只构建，不安装、启动或连接车辆。

STATIC_FLOW_VERIFIED、UNIT_TESTED、TEST_SOURCE_COMPILED、BUILD_VERIFIED仅适用于各自记录。RUNTIME_VERIFIED未执行；VEHICLE_VERIFIED未执行。整体仍有PARTIAL/BLOCKED，不将构建等同运行。

## 全部前置提交

以下74项含代码、测试和审计记录；本报告所在第75项文档提交由最终HEAD标识。

| Hash | 标题 |
|---|---|
| `ef55352c8503e677ba4a3e3c2fb431b348c69457` | fix: complete vehicle setting battery parameter safety |
| `68c7baaccbcfc04c72f2bddfbfc5cc5da860536d` | fix: complete vehicle setting timer write safety |
| `2ef8896158e5bcdcaf75ec8fe4b85e3bd84c69d9` | fix: complete vehicle setting choice write safety |
| `0e587d40374dd90b36f7d457b43bed498d9cf87f` | fix: complete vehicle setting geofence composite writes |
| `6068cf1b61434aed6de14177fbcedf9a981070c4` | fix: complete vehicle setting atmosphere composite safety |
| `13e06e7d963a59221d03d040fc4540ec5004194b` | fix: complete vehicle setting light and power switches |
| `a2e8f9088853edeab9ca8637e37994e9c97ab03e` | fix: complete vehicle setting safety assistance switches |
| `e62b59ffe98d7d97c1c1e5ef6ab9ca0244c2ec39` | fix: complete vehicle setting endurance and switch polarity |
| `204f9664cb77cda3e9a6b546807b5017ad490010` | fix: complete vehicle setting auxiliary switch readback |
| `58a83fff5da182b396ef7dc50585b936771444c2` | fix: complete vehicle setting cruise and motion switch readback |
| `efdc4267ff80bfe0849bc225836dbd655dcca303` | fix: complete vehicle setting volume and brightness readback |
| `26c3150b8bcc6986a78a1c79fe785d9ef6fcadb6` | fix: complete vehicle setting energy recovery safety |
| `041f0b126af4940b6e677533cd3d214ec1958381` | fix: complete vehicle setting cloud alarm and sound safety |
| `b0c065012d913005fd3c296a8ef2caf230af398f` | fix: complete vehicle setting remaining mileage eligibility |
| `61a9e9ab8039bd4619a10c39cd9886e21d4a80ee` | fix: complete vehicle setting side brace write safety |
| `6e5038c5c4207cb8939facbc86c97b42e52fd4ff` | fix: complete vehicle setting tracker write safety |
| `812a6c8d2ee1667b80f4f500a6e26b50cf303715` | fix: complete vehicle setting simulated sound safety |
| `fd376e3f7795bc58420c87b9213148b7c24b2c43` | fix: complete vehicle setting time sync report safety |
| `1f0cd06464fff643c9e65bca4da589910dfeee22` | fix: complete vehicle setting quiet mode write safety |
| `b4d33db6169d4bc247083daa7578be413b1239d0` | fix: complete vehicle setting immediate sleep cloud flow |
| `6b3ed36dd7fa02fd34eb992724056e076182b3de` | fix: complete vehicle setting linked immediate sleep |
| `c85cce4d6490dcde83e4278decd9063295e223bf` | fix: complete vehicle setting automatic sleep scheduling |
| `870cfb186c6383fd292d779754843e821eb9a35c` | fix: harden adaptive push vehicle settings |
| `d4dbcf46e402b7e4d056fa5c46fae7e93adec2f5` | fix: complete radar vehicle setting readback |
| `3bf4645616b18f93e4db058e94d2797796d57597` | fix: complete vehicle setting gauge sync configuration |
| `db0b0db68f0c29ea8f6f730e85c8148335bf594c` | docs: record vehicle setting repair batch A25 |
| `e305f2bdc19f90f8a748f3eb709d30779df38e67` | fix: isolate emergency contacts by vehicle VIN |
| `796fcb509bd2e708c337d5823bebdc99dd5f0e3d` | docs: record vehicle setting repair batch A26 |
| `d971f7a2e15c7430b23660959138704643da1c65` | fix: harden one-key distance setting writes |
| `d32f797a483de921c09bffc371c81cea6975927a` | docs: record id52 evidence blocker |
| `3a57244a3ffb75dbe24ab60f580702f08a79f10c` | fix: harden lost mode response handling |
| `12c1e8dcc8619fbbd67e622b83d63f70fbf0ebf1` | fix: preserve unknown password unlock status |
| `f6cb9ad285aca3797e790e1a3d92d964059fe630` | fix: keep unverified manual distance unknown |
| `e2146151d89a1bdca25d95b8cd8d08dcf3932684` | docs: record autonomous repair A31 results |
| `5b357d6011d21b21edade275b78cdc046b15e284` | test: align endurance mode regression with inline path |
| `9a272cd77ac8092cc1b7b4394949a004e8748237` | docs: record autonomous repair A32 results |
| `1c139fa10e89b80fffb8fb254796f14406e24f74` | test: align OTD regression with inline setting path |
| `bf9a3d62ba9aac8f9330bc5adbf94c022411e8fd` | docs: record autonomous repair A33 results |
| `292f9c3040fa417006faea0034b85d15e7e2001e` | test: preserve unknown readback for write-only settings |
| `eecc462997593985a683efd17d9e6a8801dbaa2f` | docs: record autonomous repair batch A34 |
| `0e462bd093173a7c343ec42677b277a9eec6eeee` | fix: harden custom key setting commands |
| `41a559ce7d2915ebd825e81391529d367aca5a9f` | docs: record A35 repair commit |
| `e5b9b968055a7841f1612ff06f10cfc0a3a2ff06` | test: align volume fixtures with confirmed three-level mapping |
| `f93c4dce1d11627fb2dc678b2e80d62b7347b84f` | docs: record vehicle setting repair batch A36 |
| `4e20f455143869f66acda8efa0f61cb9e63e65d5` | fix: complete X1 vehicle setting mappings |
| `3a64467e034ab5ead89b7ab8bcc12a287a041946` | docs: record vehicle settings batch A37 |
| `34564825927ebdb22aa2c94e1e3369fc6ca9519e` | docs: record radar alarm evidence gap |
| `70c42febc3199a7a540b20197a8311f8747750bc` | fix: require live parent state for dependent settings |
| `5eb700dd67f0a027704ffca34d6fec9be10ab2e5` | fix: complete TTP readback for settings 3 and 13 |
| `61cbb57131b52e6100134d08aa4700118db0265b` | docs: clarify side brace close conditions |
| `473a6f44b437812e82fd4c4bf76b4a690fe08438` | docs: audit emergency contact platform wiring |
| `569a0e98d54abd1c599bff0a61f7a854b7a2bd34` | fix: complete vehicle setting charge power readback |
| `7c28f1085ce795da15ced4894f27bc45a7f7fe05` | docs: record charging power repair commit |
| `5ce5589b2f03830476d7d621dc57affbe0d8269b` | docs: audit battery and heated seat settings |
| `ab7b99b663d4c30e1718c527aa3d88111352f44b` | fix: preserve unknown seat heating state |
| `c23444da05babdd93cb6eca51ee690622e6060a5` | docs: record ID62 repair commit |
| `97141aa270898c7d07f563eb9120c6e0dd1a4b7f` | fix: add heated seat schedule management |
| `3f61fe15e311a0631146c6a2177ada095ecbf556` | docs: record heated seat schedule repair |
| `087fb4519e6f2a9ef46357166a1fb8262f50b8e2` | docs: audit custom sound vehicle apply path |
| `999c2bfff356e278a13cf9a7bb77b039b74960a3` | fix: require fresh VIN-bound password readback |
| `62b8fb0b00746d66bda08a9cc718f7046e3e91ce` | docs: record custom sound BLE audit |
| `25b1e1fb59e55e839ccb55ca655653fba1794a17` | fix: gate SOS enablement on contact readiness |
| `f99cd298237a9d4ada5cb94e74a08c71e8fb17ed` | docs: record SOS contact readiness repair |
| `ba6097f4bacfbd1b89fb212213224c59129fe0a7` | docs: audit vehicle calibration capability flow |
| `083d1d07c87fd50e11298fc47e8848f3cad26a2f` | docs: record vehicle setting census and re-audit |
| `604657c5deecc92e780ac47ecbea6793f56c57a9` | docs: audit dynamic vehicle setting entrances |
| `6b75d4ae571f5019735ca60063018f51176226c3` | docs: correct laboratory entrance route evidence |
| `9cdf9439d2db4cba2415ca533c50995340e1b9f8` | fix: coordinate password setting confirmation state |
| `3c326495374424f1d149db3d92650341e71e973b` | docs: record password setting repair commit |
| `b002dea512fd343d4b2af02a21ef181d5d485aa8` | docs: record gauge time sync audit findings |
| `e04daa5bab2d22457c31eb256b3565793d226341` | docs: update vehicle settings repair queue |
| `4ec625ad6e36bb603e8763bd214a5c3e2007c174` | fix: complete vehicle setting controller forwarding |
| `7092deba14a246bc12b301639fbee52b76746dc0` | fix: complete vehicle setting instrument info sync |
| `f63bc5944592900f59eca48befdde84e3a409701` | fix: complete vehicle setting SOS contact guards |
