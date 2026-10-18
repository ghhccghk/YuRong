package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileTest {
    @Test
    fun iotRenewalUrl_usesConfirmedH5RouteAndQueryFields() {
        val profile = VehicleProfile(
            vin = "YD 1234",
            bikeNickName = "小雅 & 车主",
            iotService = VehicleIotServiceInfo(expireDays = -10),
        )
        assertEquals(
            "https://tspapph5.yadeaiot.com.cn/#/flow-recharge-iot-new-index" +
                "?vin=YD%201234&bikeNickName=%E5%B0%8F%E9%9B%85%20%26%20%E8%BD%A6%E4%B8%BB",
            IotRenewalLinks.build(profile),
        )
    }

    @Test
    fun iotExpiryLabels_followNinetyDayRule() {
        assertTrue(VehicleIotServiceInfo(expireDays = -1).isExpired)
        assertTrue(VehicleIotServiceInfo(expireDays = -91).isPermanentlyExpired)
        assertFalse(VehicleIotServiceInfo(expireDays = -90).isPermanentlyExpired)
        assertEquals("查看服务", IotRenewalLinks.actionLabel(VehicleIotServiceInfo(expireDays = -91)))
        assertEquals("立即续费", IotRenewalLinks.actionLabel(VehicleIotServiceInfo(expireDays = -10)))
    }

    @Test
    fun linkedModelIsSelectedFromDLinkTag() {
        assertTrue(VehicleProfile(dLinkTag = "1").isLinkedModel)
        assertFalse(VehicleProfile().isLinkedModel)
    }
}
