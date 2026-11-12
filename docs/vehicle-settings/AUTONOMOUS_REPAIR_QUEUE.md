# 自主修复任务队列

起点：329effd69e72a4cdf60d3d910dccde2ad1bce247，master，2026-11-12。

## 正在处理

- A56完成ID51仪表时间同步静态复核：Smali证明C0时间帧和两个延迟触发源，但KMP缺少独立操作状态与触发链。未改生产代码；当前标记PARTIAL/BLOCKED，具体证据见历史及功能地图。下一候选ID41/88自定义音效设备侧链路与雷达告警BLOCKED项复核，然后继续全矩阵其余未覆盖项。A55完成ID25密码流程与车辆确认协调的有证据局部修复；ID25仍PARTIAL，独立云端密码事务与车辆状态并非原子流程，也未做App/实车验证。A53/A54入口静态复核已结束。A50已补齐ID91开启SOS前的同VIN完整联系人门槛；ID91云端配置链路静态完整，仍未做App/云端/车辆运行验证。A46已补齐ID62独立座椅加热排程CRUD；ID62总体仍PARTIAL。A47/A49确认ID41/88设备侧BLE音效应用保持PARTIAL/BLOCKED。

## 待处理（候选，须先证据复核）

- ID41/88自定义音效：A47/A49确认KMP云端目录/资源管理与结果轮询已接通，但原App另有`BLE_CLEAR_SOUND`、场景操作及`BleCustomSoundControl`专用GATT分片状态机。Smali路径是本地`uploadFilePath`→FileInputStream全文件字节→设备握手/元数据→256字节块与逐片应答；KMP无同等调用链，非Android适配器Unsupported。保持PARTIAL/BLOCKED；等待本地文件/云端资源如何取得可发送字节及跨平台传输设计形成可验证契约前，不新增帧、Writer或平台旁路。
- 雷达告警模式/音量（A38）：`COMMAND_RADAR_ALARM`/0x12F 的参数来自 `RadarActivity.mCurrentPosition`，但完整允许值、UI边界和该参数与任何 TTP 字段之间的映射尚未证实；保持 BLOCKED，不接入写入。若后续获得可信资源/绑定或抓包证据再复核。
- 回归维护补充：A36已提交，EighthRoundValueStateTest和SixthRoundActivityBatchTest中ID20的合法编辑/命令夹具限定到原页三档0..2；3..6及解析哨兵-1仅作为UNKNOWN拒绝样本。
- 回归维护：Phase4CarSettingsTest的62索引、10围栏快照及ID72/80动态页方向夹具已按对应调用路径修正；ID72/80独立页与动态页行为差异仍需保留审计，不合并语义。
- A38雷达告警继续BLOCKED；ID3/13读取缺口已由A40解决，写入接受仍不等于车辆确认。
- ID24/85：A42确认Android已注入联系人Controller，Desktop未注入；iOS/JS/Wasm的HTTP传输明确不可用。等待各平台认证头/device identity与HTTP transport的正式实现，不用空适配或Android默认请求头伪造支持。
- ID91：A50确认云端配置读写字段和原页开关前置联系人校验；当前完整资料来自既有联系人Gateway，非Android平台的联系人Controller/HTTP能力仍受A42限制，缺联系人资料时保守禁用开启。
- ID66：A51确认`1002/66`是原App一键开机校准能力/引导门控，不是已有证据支持的普通云端开关或BLE设置；KMP保持只读。等待完整钥匙校准事务及车辆结果协议证据，不从自动/手动引导名推断命令。
- A53动态入口复核：ID95继续按项目规范隐藏。`special:meter_wallpaper_skin`、`special:meter_gauge_sync`、`special:footer_intelligence`、`special:footer_lab`及`footer_device_detail`均是独立页面入口而非普通设置字段；壁纸传输、家庭车设置页、实验室聚合页与车辆详情需按独立流程继续审计（A54更正：实验室回调a0→LabActivity，不是E0→RadarActivity），不把标题/入口ID映射为云端或BLE参数。
- 其他全部项：按功能地图逐项核对读取、写入、门控及独立流程；发现安全问题优先插队。
- 基线4/58/61/55/92、9/82：检查当前代码并回归，避免重复历史修复。
- ID5/7/11/39：A39/A52重新核对页面、TTP回填、命令和KMP入口。ID5未知Choice走显式选项面板且最终值校验为0/1；ID11开关/距离独立回填且距离受同VIN实时父开关门控；无新增可安全修复缺口。ID7/39原页`VehicleDataManager.W0`的1021/29车型门控在KMP缺少等价可信来源，继续保留UNKNOWN/PARTIAL，不用默认值放开。
- ID62即时加热云端读取绑定与任务实际执行确认仍为PARTIAL/BLOCKED；A46已完成其独立任务 CRUD，禁止将任务字段并入confId=62档位或将云端接受表述为车辆确认。
- ID25：A48/A55已阻止旧/迟到TTP结果误确认本次写入，确认窗口按Smali从BLE发送返回后计2000ms；云端、BLE与车辆确认独立。密码重置云端与设备状态仍非原子链路，整体PARTIAL。

## 证据限制候选

- 52：A31已关闭缺省`bleDistance=3`伪造、未证实字段回填/云更新和最终Writer BLE fallback。`CustomPowerOnDistancesActivity`/`CustomPowerOnDistancesViewModel`运行独立测距流程并上报`phoneBluetoothRecord/add`；测距结果与云端`bleDistance`关联仍未证实，保持BLOCKED，不增命令。

- 42：A14已追清jp6.h/i→cal2→VM.x→k5。资格响应缺失/非法时阻止开启；未运行App或实车验证。
- 13：A40已接通`l76`解析→`TtpExtendedInfo`→Controller→ID13 UI的LIVE_TTP读取；只接受raw 0/1。原写入路径保留，运行态/车辆确认未验证。
- 3：A40已接通`l76`解析→`TtpExtendedInfo`→Controller→ID3 UI的LIVE_TTP读取；只接受raw 0/1。现有11/12写入保持原状，运行态/车辆确认未验证。
- 2：A41确认原页特定关闭命令要求w2非空、H6/F1为true且W0为true；W0检查VehicleDataBean.vehicleConfig父1021及子29。KMP未证实H6/F1对应来源，特定车型分支保持BLOCKED；继续仅允许已有证明安全的路径，不新增帧或推断权限映射。
- 75：原页和内联均读iotRemoveAlarm；P4/s20对非null Boolean固定写tamperProofIotEnable=1，0关闭缺证据，已阻止。原FA帧受保护边界限制；不补帧、不将字段名当云端配置读取证据。
- 51：A56已核对`ble_sync_gauge_time`事件、`MainActivityLogic.q0`及C0编码分支；KMP缺独立同步操作状态与触发路径。不能与C1开关写入共用一个结果；保持PARTIAL/BLOCKED，待实现独立操作及失败重试状态。
- 77排程、33模式、93当前值、复杂独立页面：复核后逐项列出BLOCKED/PARTIAL原因；此处不是最终判定。

## 恢复规则

每批开始读本文件、地图、历史、HEAD和staged/unstaged/untracked；只从最近成功提交后继续。禁止清理或暂存用户原有12项未跟踪内容。未通过测试、构建、自审及精确diff时不提交；不调用真实网络/车辆。

## 已完成

- A55：ID25密码云端提交、BLE发送与车辆确认状态协调；按Smali `sendUnlockPasswordSetting:310–343` 将确认窗口置于BLE发送返回后并限定2000ms，显式隔离消息tone。Fake-only 17/17；JVM/JS/Wasm/测试源码编译、Android Debug/Desktop构建、`git diff --check`通过。ID25仍PARTIAL；无App运行或车辆验证。实现提交`9cdf943`。

- A53：静态复核五个special动态/页脚入口的车型/用户门控及点击路由；更新矩阵分类。ID95主用户与云端目录门控已核实，KMP隐藏符合仓库约束。未改代码；文档-only，`git diff --check`通过后精确本地提交；未运行测试、构建、App、网络、BLE或车辆操作。

- A52：重扫直接`getSettingConfigId()`，72个方法/70个唯一ID均覆盖于矩阵；识别3个动态`getSettingConfigIdList()`入口。只读复核ID5/7/11/39的Smali与KMP调用链，未发现可安全新增的字段或写入；7/39车型门控来源仍BLOCKED。仅更新矩阵/队列/历史，未运行测试或构建；无App、网络、BLE或车辆操作。

- A46：ID62独立座椅加热排程，新增/更新/列表/删除/启停接入现有签名 HTTP Gateway 和局部 BottomSheet；严格UNKNOWN、VIN、权限、最多3项、HH:mm、重复/周日0映射、`status=0`开/`1`关及显式成功响应校验。Fake-only过滤测试6/6，JVM/JS/Wasm/测试源码编译、Android Debug/Desktop构建通过。实现提交`97141aa`；审计记录单独提交。

- A47/A49：ID41/88自定义音效云端调用链和专用BLE状态机均已静态审计；KMP缺少音效传输调用链，非Android BLE适配器Unsupported，故保持PARTIAL/BLOCKED，不改协议。无测试/构建/运行，详见历史。

- A45：ID62移除未经Smali读取绑定证实的`seatHeating`/`seatHeatingLevel` configuredValue映射；未知开关/档位不再显示为OFF或默认选择，Controller阻止UNKNOWN目标编辑/提交。Fake 19/19、跨平台编译和Android/Desktop构建通过；实现提交`ab7b99b663d4c30e1718c527aa3d88111352f44b`。

- A44：ID60容量配置及TTP回读、ID62即时加热开关/档位静态复核完成；云端容量开关无原App回填证据，不加映射。ID62独立排程仍待审/未实现，整体PARTIAL。Fake隔离测试通过；无代码改动。

- A43：ID47原App TTP `0x71`乘50W，修正KMP此前×2导致读取值落在合法范围之外的问题；补解析Fake用例。ID48 APK布局确认75..100 UI范围及原始云字段/离散BLE分流，未改写入语义；验证通过，功能提交`569a0e98d54abd1c599bff0a61f7a854b7a2bd34`。

- A42：审计ID24/85联系人能力的平台注入。Android已实例化；Desktop入口缺注入；iOS/JS/Wasm transport不可用，保持PARTIAL/BLOCKED，不改通用网络层。静态验证，无App或网络验证。
- A41：只读复核ID2边撑感应车型关闭分支，确认W0要求父1021/子29且原页另需H6/F1；映射证据不足，维持BLOCKED。更新矩阵/队列/历史，仅静态验证。提交`61cbb57`。

- A40：ID3/13读取补全；Fake过滤测试54/54，JVM/JS/Wasm及测试源码编译通过；Android/Desktop构建通过。矩阵已更新；无App运行或实车验证。

- A37：001/002/003 X1 TTP/百分比档位映射及002开启门控；Fake测试7/7，JVM/JS/Wasm、Android Debug、Desktop构建通过；无App/实车验证。提交`4e20f45`。

- A38：雷达告警滑杆静态复核；未改业务代码。Smali确认 `RadarActivity$a.a` 在允许条件下提交 `COMMAND_RADAR_ALARM(mCurrentPosition)`，`qg.smali` 将其映射到 `0x12F`；`VehicleSettingViewModel` 将 `fcwRadarMode` 单独写入 LiveData，现有证据未证明它就是该滑杆值。范围、选项/单位及可信回填仍 BLOCKED。文档提交后停止本批；无测试或构建（仅文档更新），无 App/车辆验证。

- A39：ID7/11/39子项门控此前读取`displayKind()`，缓存、草稿或提交目标可能被当作父开关依据。现要求父项为同VIN、有效LIVE_TTP开启观察，且无草稿/提交/在途写入。Fake测试27/27；JVM/JS/Wasm/测试源码编译及Android Debug/Desktop构建通过。7/39的`VehicleDataManager.W0`车型条件仍无法由KMP证明，保持PARTIAL/UNKNOWN，无App/实车验证。

- A36：ID20音量回归夹具改用Smali证实的0..2三档，并模拟UI编辑的`valueKnown`语义；7项Fake过滤测试、跨平台编译、Android Debug/Desktop构建通过。提交`e5b9b968055a7841f1612ff06f10cfc0a3a2ff06`。

- A34：隔离ID3/13的WRITE_ONLY/读取BLOCKED状态测试；确保无原始TTP字段时车辆值/缓存继续UNKNOWN，写入后只保持WAITING_READBACK，并按Smali验证0x11/0x12及0x62/0x63命令码。13项Fake过滤测试、JVM/JS/Wasm/Android/Desktop构建通过。提交`292f9c3040fa417006faea0034b85d15e7e2001e`。

- A35：ID1005短按/长按自定义按键。保留Smali回填语义（0..8显示对应功能，其他有效nibble显示“无定义”，0xF按UNKNOWN）；禁止UNKNOWN占位值提交，拒绝伪造条目ID、非法Choice索引、错配命令类型/ID和额外参数。4个Fake用例通过；JVM/JS/Wasm/测试源码编译及Android/Desktop离线构建通过；无App运行/实车验证。功能提交`0e462bd093173a7c343ec42677b277a9eec6eeee`。

- A33：区分ID80动态`bikeConfigVos`内联写入与独立`OTDActivity`的反向参数行为；KMP动态路径符合内联Smali，校正Phase4回归断言并新增精确Fake用例。提交`1c139fa10e89b80fffb8fb254796f14406e24f74`。

- A32：区分ID72动态`bikeConfigVos`内联写入与独立Activity两种不同极性；KMP动态路径符合内联Smali，修正回归聚合断言并新增精确Fake用例。提交`5b357d6011d21b21edade275b78cdc046b15e284`。

- A31：ID52未知距离值保持UNKNOWN/只读，移除未经Smali证实的云字段映射，并在最终Writer拒绝Slider以阻断BLE/云端旁路。56项Fake过滤测试、JVM/JS/Wasm/测试源码编译、Android/Desktop构建通过；ID52测距字段关系仍BLOCKED。提交`f6cb9ad285aca3797e790e1a3d92d964059fe630`。

- A30/A48：ID25密码解锁状态来源隔离和新鲜回读确认门控已完成；云端密码设定、BLE发送和车辆回读仍为独立状态。跨云端密码管理、失败/超时协调仍PARTIAL。

- A29：ID8 Smali确认`sendCode=10`、`smsScene=10`，短信验证后调用`common-cmd/low-freq/set-param/loss`，`value=00/01`；云端状态`lossState=0`表示开启。KMP已有独立短信/报失Gateway、Controller和诊断UI；修正2xx响应缺少`success=true`时误报成功。修正过期Fake对签名表单的断言。Fake整类过滤6/6，跨平台编译与Android/Desktop构建通过；服务端接受仍不是车辆确认，无真实请求/App/车辆操作。提交记录见历史。

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
