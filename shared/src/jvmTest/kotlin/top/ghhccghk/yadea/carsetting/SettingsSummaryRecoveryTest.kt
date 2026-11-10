package top.ghhccghk.yadea.carsetting

import org.junit.Assert.*
import org.junit.Test
import top.ghhccghk.yadea.ui.carSettingSummary
import top.ghhccghk.yadea.ui.settingDescriptionSummary

/** Presentation-only tests: data, cloud permissions and dynamic items must remain intact. */
class SettingsSummaryRecoveryTest {
    private fun item(channel: CarSettingChannel = CarSettingChannel.IOT, summary: String = "云端车辆设置项") =
        CarSettingItem("cloud:13", "报警音", summary, CarSettingClassification.SECURITY,
            channel = channel, kind = CarSettingKind.Toggle(false, valueKnown = false), configId = "13")

    @Test fun removesOnlyGenericCloudPlaceholderAndIotChannelSummary() {
        val item = item()
        val result = carSettingSummary(item, CarSettingValueState(), false, null)
        assertFalse(result.contains("云端车辆设置项"))
        assertFalse(result.contains("车联网"))
        assertTrue(result.contains("车辆当前值：UNKNOWN"))
        assertEquals("云端车辆设置项", item.summary)
        assertEquals(CarSettingChannel.IOT, item.channel)
        assertTrue(item.writable)
    }

    @Test fun preservesUnrelatedDescriptionsReadOnlyReasonsAndBleChannel() {
        val item = item(CarSettingChannel.BOTH, "检测异常移动时提醒").copy(writable = false)
        val result = carSettingSummary(item, CarSettingValueState(), false, "请先连接车辆")
        assertTrue(result.startsWith("检测异常移动时提醒 · 蓝牙"))
        assertFalse(result.contains("车联网"))
        assertTrue(result.contains("只读"))
        assertTrue(result.contains("请先连接车辆"))
    }

    @Test fun unknownOrDisconnectedDoesNotRemoveDescription() {
        val item = item(summary = "保留的云端功能说明")
        assertTrue(carSettingSummary(item, CarSettingValueState(), false, "未连接").contains(item.summary))
        assertEquals(item.summary, settingDescriptionSummary(item))
    }

    @Test fun emptyDescriptionsDoNotCreateLeadingSeparators() {
        for (description in listOf("", "云端车辆设置项")) {
            val result = carSettingSummary(item(summary = description), CarSettingValueState(), false, null)
            assertFalse(result.startsWith(" · "))
            assertEquals("车辆当前值：UNKNOWN", result)
        }
    }

    @Test fun pendingFailureDraftAndReceiptDetailsRemainVisible() {
        val state = CarSettingValueState(draftValue = CarSettingKind.Toggle(true),
            submittedValue = CarSettingKind.Toggle(false), writeState = SettingWriteState.FAILED,
            confirmationState = SettingConfirmationState.TIMED_OUT, error = "Fake failure")
        val result = carSettingSummary(item(), state, true, null)
        for (text in listOf("正在保存", "草稿", "本次选择", "写入失败", "确认超时", "Fake failure")) {
            assertTrue(text, result.contains(text))
        }
    }

    @Test fun presentationDoesNotFilterDynamicSettingsOrClearRawCloudCopy() {
        val cloud = CloudCarSettings(listOf(CloudBikeConfig("13", briefCopy = "保留说明", isAuth = false),
            CloudBikeConfig("64"), CloudBikeConfig("89")), emptyMap())
        val items = CloudCarSettingMapper.toItems(cloud)
        val before = items.toList()
        items.forEach { carSettingSummary(it, CarSettingValueState(), false, null) }
        assertEquals(before, items)
        assertTrue(items.any { it.configId == "13" && !it.writable && it.summary == "保留说明" })
        assertTrue(items.any { it.configId == "64" })
        assertTrue(items.any { it.configId == "89" })
    }
}
