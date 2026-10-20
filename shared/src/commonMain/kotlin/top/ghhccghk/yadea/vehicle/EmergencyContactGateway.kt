package top.ghhccghk.yadea.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ghhccghk.yadea.auth.Crypto
import top.ghhccghk.yadea.auth.GatewaySigner
import top.ghhccghk.yadea.network.GatewayClientInfo
import top.ghhccghk.yadea.network.GatewayRequestHeaders
import top.ghhccghk.yadea.network.HttpRequest
import top.ghhccghk.yadea.network.HttpTransport
import top.ghhccghk.yadea.network.JsonSupport
import top.ghhccghk.yadea.network.NetworkLog

/** 车辆紧急联系人页面数据，对应 `EmergencyContactsData`。 */
data class EmergencyContacts(
    val vin: String = "",
    val ownerName: String = "",
    val ownerMobile: String = "",
    val emergencyName: String = "",
    val emergencyMobile: String = "",
)

interface EmergencyContactGateway {
    suspend fun load(vin: String): EmergencyContacts?
    suspend fun save(vin: String, type: ContactType, name: String, mobile: String): Boolean
}

enum class ContactType(val value: String) {
    Owner("1"),
    Emergency("2"),
}

/**
 * `vehiclebase/bikeEmergencyContact/select` 与 `/insert`。
 *
 * `type=1` 是车主，`type=2` 是紧急联系人；原页面允许分别编辑。
 */
class YadeaEmergencyContactGateway(
    private val transport: HttpTransport,
    private val tokenProvider: () -> String,
    private val deviceIdProvider: () -> String = { "" },
    private val clientInfoProvider: () -> GatewayClientInfo = { GatewayClientInfo() },
    private val timestampProvider: () -> Long,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : EmergencyContactGateway {

    override suspend fun load(vin: String): EmergencyContacts? {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank()) return null
        val businessJson = sortedJson(mapOf("_gateway" to "True", "vin" to vin))
        val url = baseUrl + SELECT_PATH + "?" +
            GatewaySigner.formBody(businessJson, timestampProvider())
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("CONTACT -> GET $url")
        val response = transport.execute(HttpRequest("GET", url, headers))
            ?: return null
        NetworkLog.write("CONTACT <- ${response.status} raw=${NetworkLog.body(response.body)}")
        if (response.status !in 200..299) return null
        val plain = decrypt(response.body)
        NetworkLog.write("CONTACT plain ${NetworkLog.maskSensitive(plain)}")
        if (JsonSupport.boolean(plain, "success") == false) return null
        val data = JsonSupport.objectField(plain, "data") ?: return null
        return EmergencyContacts(
            vin = stringField(data, "vin").orEmpty().ifBlank { vin },
            ownerName = stringField(data, "userName").orEmpty(),
            ownerMobile = stringField(data, "userMobile").orEmpty(),
            emergencyName = stringField(data, "emergencyContactName").orEmpty(),
            emergencyMobile = stringField(data, "emergencyContactMobile").orEmpty(),
        )
    }

    override suspend fun save(
        vin: String,
        type: ContactType,
        name: String,
        mobile: String,
    ): Boolean {
        val token = tokenProvider().trim()
        if (token.isBlank() || vin.isBlank() || name.isBlank() || mobile.isBlank()) return false
        val body = GatewaySigner.formBody(
            sortedJson(
                mapOf(
                    "_gateway" to "True",
                    "vin" to vin,
                    "name" to name.trim(),
                    "mobile" to mobile.trim(),
                    "type" to type.value,
                ),
            ),
            timestampProvider(),
        )
        val url = baseUrl + INSERT_PATH
        val headers = GatewayRequestHeaders.build(
            clientInfo = clientInfoProvider(),
            deviceId = deviceIdProvider(),
            token = token,
        )
        NetworkLog.write("CONTACT -> POST $url headers=${NetworkLog.headers(headers)}")
        val response = transport.execute(
            HttpRequest(
                method = "POST",
                url = url,
                headers = headers,
                body = body,
                contentType = "application/x-www-form-urlencoded; charset=utf-8",
            ),
        ) ?: return false
        NetworkLog.write("CONTACT <- ${response.status} raw=${NetworkLog.body(response.body)}")
        if (response.status !in 200..299) return false
        val plain = decrypt(response.body)
        return JsonSupport.boolean(plain, "success") != false
    }

    private fun decrypt(raw: String): String {
        val decoded = Crypto.urlDecode(raw.trim())
        return Crypto.aesEcbPkcs5Decrypt(GatewaySigner.AES_KEY, decoded) ?: decoded
    }

    private fun sortedJson(fields: Map<String, String>): String = buildString {
        append('{')
        fields.keys.sorted().forEachIndexed { index, key ->
            if (index > 0) append(',')
            append('"').append(key).append("\":\"")
                .append(fields.getValue(key).replace("\\", "\\\\").replace("\"", "\\\""))
                .append('"')
        }
        append('}')
    }

    private fun stringField(json: String, key: String): String? =
        JsonSupport.string(json, key)

    companion object {
        const val SELECT_PATH = "vehiclebase/bikeEmergencyContact/select"
        const val INSERT_PATH = "vehiclebase/bikeEmergencyContact/insert"
        const val DEFAULT_BASE_URL = "https://gw.yadeaiot.com.cn/api/app/"
    }
}

data class EmergencyContactUiState(
    val loading: Boolean = false,
    val savingType: ContactType? = null,
    val contacts: EmergencyContacts? = null,
    val message: String? = null,
    val success: Boolean = false,
)

class EmergencyContactController(
    private val gateway: EmergencyContactGateway?,
    private val vinProvider: () -> String,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EmergencyContactUiState())
    val state: StateFlow<EmergencyContactUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load() {
        val gateway = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank() || job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(loading = true, message = null)
            val contacts = runCatching { gateway.load(vin) }.getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                contacts = contacts ?: _state.value.contacts,
                message = if (contacts == null) "紧急联系人加载失败" else null,
                success = contacts != null,
            )
        }
    }

    fun save(type: ContactType, name: String, mobile: String) {
        val gateway = gateway ?: return
        val vin = vinProvider().trim()
        if (vin.isBlank() || job?.isActive == true) return
        job = scope.launch {
            _state.value = _state.value.copy(
                savingType = type,
                message = null,
                success = false,
            )
            val saved = runCatching { gateway.save(vin, type, name, mobile) }
                .getOrDefault(false)
            val contacts = if (saved) {
                runCatching { gateway.load(vin) }.getOrNull() ?: _state.value.contacts
            } else {
                _state.value.contacts
            }
            _state.value = _state.value.copy(
                savingType = null,
                contacts = contacts,
                message = if (saved) "联系人已保存" else "联系人保存失败",
                success = saved,
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dispose() {
        job?.cancel()
        job = null
    }
}
