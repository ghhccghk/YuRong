package top.ghhccghk.yadea.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import top.ghhccghk.yadea.auth.AuthController
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.ota.OtaController
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehicleProfile

/** 车辆页的子功能。 */
internal enum class VehicleSection(val label: String) {
    Info("信息"),
    Garage("车库"),
    Settings("设置"),
    Ota("OTA"),
    Account("账号"),
}

/**
 * 车辆页宿主：车辆信息、设置、OTA 与账号。
 *
 * 每个子页各自拥有 LazyColumn；分段控件只切换内容，不嵌套滚动容器。
 */
@Composable
internal fun VehicleHubScreen(
    section: VehicleSection,
    onSectionChange: (VehicleSection) -> Unit,
    status: TtpInfo?,
    connected: Boolean,
    profile: VehicleProfile?,
    profiles: List<VehicleProfile>,
    onSelectVehicle: (String) -> Unit,
    vehicleBackgroundContent: (@Composable (String, Modifier) -> Unit)? = null,
    vehicleImageContent: (@Composable (String, Modifier) -> Unit)? = null,
    onOpenIotRenewal: ((String) -> Unit)? = null,
    authController: AuthController,
    carSettingsController: CarSettingsController,
    otaController: OtaController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val dimensions = AppDesign.dimensions
    LaunchedEffect(status) {
        otaController.updateCurrentVersion(VehicleInfoFormatter.softwareVersion(status))
    }
    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        AppSegmentedControl(
            options = VehicleSection.entries.map { it.label },
            selectedIndex = VehicleSection.entries.indexOf(section),
            onSelected = { onSectionChange(VehicleSection.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(
                start = dimensions.pageHorizontalPadding,
                top = dimensions.controlSpacing,
                end = dimensions.pageHorizontalPadding,
            ),
        )
        Box(modifier = Modifier.weight(1f)) {
            val pagePadding = appPagePadding(PaddingValues())
            when (section) {
                VehicleSection.Info -> VehicleInfoScreen(
                    status = status,
                    connected = connected,
                    contentPadding = pagePadding,
                    profile = profile,
                    vehicleBackgroundContent = vehicleBackgroundContent,
                    vehicleImageContent = vehicleImageContent,
                    onOpenIotRenewal = onOpenIotRenewal,
                )
                VehicleSection.Garage -> GarageScreen(
                    profiles = profiles,
                    selectedVin = profile?.vin.orEmpty(),
                    contentPadding = pagePadding,
                    onSelectVehicle = onSelectVehicle,
                )
                VehicleSection.Settings -> CarSettingsScreen(
                    controller = carSettingsController,
                    contentPadding = pagePadding,
                )
                VehicleSection.Ota -> OtaScreen(
                    controller = otaController,
                    contentPadding = pagePadding,
                )
                VehicleSection.Account -> LoginScreen(
                    controller = authController,
                    contentPadding = pagePadding,
                )
            }
        }
    }
}
