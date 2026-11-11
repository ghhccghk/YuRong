package top.ghhccghk.yadea.carsetting

/** Original s20.e / VehicleSettingViewModel.P4 preserve all four fence fields. */
data class GeofenceConfiguration(
    val vin: String,
    val enabled: Boolean?,
    val radius: Int?,
    val longitude: Double?,
    val latitude: Double?,
) {
    val complete: Boolean get() = vin.isNotBlank() && enabled != null && radius in 200..2000 &&
        longitude?.isFinite() == true && latitude?.isFinite() == true
}

internal object GeofenceSettings {
    fun snapshot(cloud: CloudCarSettings, vin: String) = GeofenceConfiguration(
        vin.trim(), cloud.boolValue("distanceOn"),
        cloud.values["distance"]?.trim()?.toIntOrNull()?.takeIf { it in 200..2000 },
        cloud.values["centerCircleLon"]?.toDoubleOrNull()?.takeIf { it.isFinite() },
        cloud.values["centerCircleLat"]?.toDoubleOrNull()?.takeIf { it.isFinite() },
    )

    fun validTarget(kind: CarSettingKind): Boolean = when (kind) {
        is CarSettingKind.Toggle -> kind.valueKnown
        is CarSettingKind.Slider -> kind.valueKnown && kind.rangeKnown && kind.range == 200f..2000f &&
            kind.value.isFinite() && kind.value == kind.value.toInt().toFloat() && kind.value in 200f..2000f
        else -> false
    }

    fun validItem(item: CarSettingItem): Boolean = item.configId == "10" && validTarget(item.kind) &&
        item.id == if (item.kind is CarSettingKind.Toggle) "cloud:10" else "cloud:10:distance"

    fun matches(snapshot: GeofenceConfiguration?, kind: CarSettingKind): Boolean = when (kind) {
        is CarSettingKind.Toggle -> snapshot?.enabled == kind.checked
        is CarSettingKind.Slider -> snapshot?.radius?.toFloat() == kind.value
        else -> false
    }
}
