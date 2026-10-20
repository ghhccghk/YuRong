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
import top.ghhccghk.yadea.auth.AuthUser
import top.ghhccghk.yadea.carsetting.CarSettingsController
import top.ghhccghk.yadea.ota.OtaController
import top.ghhccghk.yadea.vehicle.TtpInfo
import top.ghhccghk.yadea.vehicle.VehicleInfoFormatter
import top.ghhccghk.yadea.vehicle.VehicleProfile
import top.ghhccghk.yadea.vehicle.VehicleCloudStatus
import top.ghhccghk.yadea.vehicle.VehicleBindingController
import top.ghhccghk.yadea.vehicle.VehicleControlController
import top.ghhccghk.yadea.vehicle.VehicleFaultController
import top.ghhccghk.yadea.vehicle.LostModeController

/** 车辆页的子功能。 */
internal enum class VehicleSection(val label: String) {
    Info("信息"),
    Control("控制"),
    Diagnostics("诊断"),
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
    onRefreshVehicles: (() -> Unit)? = null,
    vehiclesRefreshing: Boolean = false,
    vehiclesErrorMessage: String? = null,
    vehicleCloudStatus: VehicleCloudStatus? = null,
    vehicleFaultController: VehicleFaultController,
    lostModeController: LostModeController,
    accountMobile: String,
    onRefreshVehicleCloudStatus: ((String) -> Unit)? = null,
    vehicleBindingController: VehicleBindingController? = null,
    currentUser: AuthUser? = null,
    authController: AuthController,
    carSettingsController: CarSettingsController,
    otaController: OtaController,
    vehicleControlController: VehicleControlController,
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
                    onRefresh = onRefreshVehicles,
                    refreshing = vehiclesRefreshing,
                    profileErrorMessage = vehiclesErrorMessage,
                    cloudStatus = vehicleCloudStatus,
                )
                VehicleSection.Control -> VehicleControlScreen(
                    controller = vehicleControlController,
                    connected = connected,
                    contentPadding = pagePadding,
                )
                VehicleSection.Diagnostics -> VehicleDiagnosticsScreen(
                    status = status,
                    cloudStatus = vehicleCloudStatus,
                    connected = connected,
                    profile = profile,
                    faultController = vehicleFaultController,
                    lostModeController = lostModeController,
                    accountMobile = accountMobile,
                    contentPadding = pagePadding,
                    onRefreshCloudStatus = {
                        profile?.vin?.takeIf { it.isNotBlank() }
                            ?.let { onRefreshVehicleCloudStatus?.invoke(it) }
                    },
                )
                VehicleSection.Garage -> GarageScreen(
                    profiles = profiles,
                    selectedVin = profile?.vin.orEmpty(),
                    contentPadding = pagePadding,
                    onSelectVehicle = onSelectVehicle,
                    onRefresh = onRefreshVehicles,
                    refreshing = vehiclesRefreshing,
                    errorMessage = vehiclesErrorMessage,
                    bindingController = vehicleBindingController,
                    currentUser = currentUser,
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
