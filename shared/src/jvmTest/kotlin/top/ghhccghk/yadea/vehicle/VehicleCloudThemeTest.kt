package top.ghhccghk.yadea.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCloudThemeTest {
    @Test
    fun loveCarBackgroundUsesConfirmedRangeSlot() {
        val theme = parseVehicleCloudTheme(
            themeConfigJson = """
                {
                  "bikeGameThemeVOList": [],
                  "userApplyThemeVo": {
                    "themeType": 1,
                    "themeImageVos": [
                      {"pictureUrl":"https://cdn/unchecked.png","range1":1,"range2":0},
                      {"pictureUrl":"https://cdn/checked.png","range1":1,"range2":1},
                      {"pictureUrl":"https://cdn/love-car.png","range1":1,"range2":2},
                      {"pictureUrl":"https://cdn/ignored.png","range1":9,"range2":2}
                    ]
                  }
                }
            """.trimIndent(),
            vin = "VIN-1",
        )

        assertEquals("https://cdn/love-car.png", theme.loveCarImageUrl)
        assertEquals(0, theme.themeType)
        assertFalse(theme.isCustomized)
    }

    @Test
    fun customizedThemeRequiresCurrentVinGameThemeWithBannerAsset() {
        val theme = parseVehicleCloudTheme(
            themeConfigJson = """
                {
                  "bikeGameThemeVOList": [
                    {"vin":"OTHER","modelNo":"M","colorType":1},
                    {"vin":"VIN-1","modelNo":"M","colorType":3}
                  ],
                  "userApplyThemeVo": {
                    "themeType": 3,
                    "themeImageVos": []
                  }
                }
            """.trimIndent(),
            vin = "VIN-1",
        )

        assertTrue(theme.isCustomized)
        assertEquals(3, theme.themeType)
        assertEquals(3, theme.gameThemeColorType)
        assertEquals(
            "https://tsposs.yadeaiot.com.cn/carh5img/app-icon/gameTheme/" +
                "vehicle_bg_pink_dark.png",
            theme.bannerVehicleBgImgUrl(darkTheme = true),
        )
    }

    @Test
    fun loveCarBackgroundAloneDoesNotMarkVehicleCustomized() {
        val theme = parseVehicleCloudTheme(
            themeConfigJson = """
                {
                  "bikeGameThemeVOList": [],
                  "userApplyThemeVo": {
                    "themeType": 0,
                    "themeImageVos": [
                      {"pictureUrl":"https://cdn/love-car.png","range1":1,"range2":2}
                    ]
                  }
                }
            """.trimIndent(),
            vin = "VIN-1",
        )

        assertTrue(theme.loveCarImageUrl.isNotBlank())
        assertFalse(theme.isCustomized)
        assertEquals("", theme.bannerVehicleBgImgUrl(darkTheme = true))
    }
}
