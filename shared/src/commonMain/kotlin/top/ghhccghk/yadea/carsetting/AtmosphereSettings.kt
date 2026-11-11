package top.ghhccghk.yadea.carsetting

import top.ghhccghk.yadea.vehicle.TtpExtendedInfo

/** FeelLightSettingActivity$c: mode-dependent composite, never completed with UI defaults. */
internal object AtmosphereSettings {
    fun valid(kind: CarSettingKind.Atmosphere): Boolean = kind.valueKnown &&
        kind.showMode in 0..3 && listOf(kind.red1, kind.green1, kind.blue1).all { it in 0..255 } &&
        (kind.showMode !in setOf(1, 3) ||
            listOf(kind.red2, kind.green2, kind.blue2).all { it in 0..255 }) &&
        (kind.showMode != 3 || kind.runMode in 0..5)

    fun validItem(item: CarSettingItem): Boolean = when (val kind = item.kind) {
        is CarSettingKind.Toggle -> item.id == "cloud:73" && kind.valueKnown
        is CarSettingKind.Atmosphere -> item.id == "cloud:73:config" && valid(kind)
        else -> false
    }

    fun observe(raw: TtpExtendedInfo): CarSettingKind.Atmosphere? {
        if (raw.atmosphereColorMode !in 0..1 ||
            listOf(raw.atmosphereRgb1r, raw.atmosphereRgb1g, raw.atmosphereRgb1b).any { it !in 0..254 } ||
            (raw.atmosphereShowMode in setOf(1, 3) &&
                listOf(raw.atmosphereRgb2r, raw.atmosphereRgb2g, raw.atmosphereRgb2b).any { it !in 0..254 })) return null
        return CarSettingKind.Atmosphere(
            showMode = raw.atmosphereShowMode, runMode = raw.atmosphereOperationMode,
            singleColor = raw.atmosphereColorMode == 0,
            red1 = raw.atmosphereRgb1r, green1 = raw.atmosphereRgb1g, blue1 = raw.atmosphereRgb1b,
            red2 = raw.atmosphereRgb2r, green2 = raw.atmosphereRgb2g, blue2 = raw.atmosphereRgb2b,
            valueKnown = true,
        ).takeIf(::valid)
    }

    fun payload(kind: CarSettingKind.Atmosphere): List<Int>? {
        if (!valid(kind)) return null
        fun rgb(v: Int) = if (v == 255) 254 else v
        val fixed = kind.showMode == 0 || kind.showMode == 2
        return listOf(kind.showMode, if (fixed || kind.singleColor) 0 else 1,
            rgb(kind.red1), rgb(kind.green1), rgb(kind.blue1),
            if (fixed) 255 else rgb(kind.red2), if (fixed) 255 else rgb(kind.green2),
            if (fixed) 255 else rgb(kind.blue2), if (kind.showMode == 3) kind.runMode else -1)
    }

    fun matches(observed: CarSettingKind.Atmosphere, target: CarSettingKind.Atmosphere): Boolean =
        payload(observed)?.let { it == payload(target) } == true
}
