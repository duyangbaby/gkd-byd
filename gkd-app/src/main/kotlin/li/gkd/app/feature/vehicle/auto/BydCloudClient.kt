package li.gkd.app.feature.vehicle.auto

import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.app
import li.gkd.app.domain.vehicle.VehicleCommand

@Serializable
data class BydSession(val username: String, val brand: String, val signToken: String, val encryptToken: String,
    val userId: String, val superId: String, val brandUserIds: Map<String, String>, val nickname: String, val controlPinHash: String)

@Serializable
data class BydVehicle(val vin: String, val name: String, val model: String, val plate: String, val brand: String)

class BydCloudException(val code: String) : Exception(when (code) {
    "22", "240", "246" -> "会话失效，请重新登录"
    "3" -> "控车密码或身份校验失败"
    "224" -> "服务器繁忙，请稍后重试"
    "242", "20" -> "账号身份或车辆权限需要确认"
    else -> "请求失败（$code）"
})

object BydCloudClient {
    private val device get() = BydDevice(Build.DEVICE, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT)
    private val codec by lazy { WbskCodec().apply { f653a = app.assets.open("byd/wbsk_tables.json").use(WbskTables::c) } }
    private val client by lazy {
        HttpClient(OkHttp) {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000; socketTimeoutMillis = 20_000 }
        }
    }

    suspend fun login(username: String, password: String, pin: String, brand: String): BydSession {
        val user = username.trim()
        val rawPassword = password.trim()
        require(user.isNotEmpty() && rawPassword.isNotEmpty() && brand in listOf("1", "2", "3", "4", "5"))
        val time = System.currentTimeMillis().toString()
        val response = request("/app/auth/login", BydProtocol.login(device, user, rawPassword, brand, time, BydProtocol.random()), brand, "502")
        val token = Json.parseToJsonElement(BydProtocol.decrypt(response.text("respondData"), BydProtocol.digest(BydProtocol.digest(rawPassword))))
            .jsonObject["token"]?.jsonObject ?: error("登录响应缺少会话")
        val relations = token["superBindRelationDtoMap"] as? JsonObject
        val users = relations?.mapValues { it.value.jsonObject.text("userId") } ?: emptyMap()
        val session = BydSession(user, brand, token.text("signToken"), token.text("encryToken").ifEmpty { token.text("encryptToken") },
            users[brand]?.ifEmpty { null } ?: token.text("superId"), token.text("superId"), users,
            token.text("nickName").ifEmpty { token.text("nickname") }, BydProtocol.digest(pin.trim()))
        check(session.signToken.isNotEmpty() && session.encryptToken.isNotEmpty() && session.userId.isNotEmpty()) { "登录响应缺少会话" }
        return session
    }

    suspend fun vehicles(session: BydSession): List<BydVehicle> {
        val time = System.currentTimeMillis().toString()
        val data = BydProtocol.deviceFields(device, session.username, time, BydProtocol.random())
        val result = tokenRequest(session, "/app/auth/getAllListByUserId", data, time, "1", "", 2,
            session.superId.ifEmpty { session.userId })
        return result["diLinkAutoInfoList"]?.jsonArray?.map { entry -> entry.jsonObject.let {
            BydVehicle(it.text("vin"), it.text("autoAlias"), it.text("outModelType").ifEmpty { it.text("cCarType") },
                it.text("autoPlate"), it.text("brandId").ifEmpty { it.text("targetBrand").ifEmpty { session.brand } })
        } }?.filter { it.vin.isNotEmpty() } ?: emptyList()
    }

    suspend fun realtime(session: BydSession, vehicle: BydVehicle): JsonObject {
        var serial = ""
        repeat(5) { attempt ->
            val time = System.currentTimeMillis().toString()
            val base = BydProtocol.fields("energyType" to "0", "tboxVersion" to "3", "vin" to vehicle.vin)
            val deviceData = JsonObject(BydProtocol.deviceFields(device, session.username, time, BydProtocol.random()).filterKeys { it != "appUiName" })
            val data = JsonObject(base + deviceData + (if (serial.isEmpty()) emptyMap() else BydProtocol.fields("requestSerial" to serial)))
            val result = tokenRequest(session, "/vehicleInfo/vehicle/vehicleRealTime${if (serial.isEmpty()) "Request" else "Result"}",
                data, time, vehicle.brand, vehicle.vin)
            if (result.text("time").toLongOrNull()?.let { it > 0 } == true) return result
            serial = result.text("requestSerial").ifEmpty { serial }
            if (attempt < 4) delay(2000)
        }
        error("车辆可能休眠，暂未返回实时车况")
    }

    suspend fun control(session: BydSession, vehicle: BydVehicle, command: VehicleCommand): Boolean {
        val time = System.currentTimeMillis().toString()
        val type = when (command) {
            VehicleCommand.UNLOCK -> "OPENDOOR"
            VehicleCommand.LOCK -> "LOCKDOOR"
            VehicleCommand.POWER_ON -> "OPENAIR"
            VehicleCommand.POWER_OFF -> "CLOSEAIR"
            VehicleCommand.CLOSE_WINDOWS -> "CLOSEWINDOW"
        }
        val base = BydProtocol.deviceFields(device, session.username, time, BydProtocol.random()).toMutableMap().apply {
            remove("appUiName"); put("networkType", JsonPrimitive("WiFi"))
        }
        val extra = BydProtocol.fields("tboxVersion" to "3", "commandPwd" to session.controlPinHash, "commandType" to type,
            "vin" to vehicle.vin, "source" to "app")
        val data = JsonObject(base + extra + if (command == VehicleCommand.POWER_ON) BydProtocol.fields("controlParamsMap" to
            BydProtocol.fields("mainSettingTemp" to 10, "copilotSettingTemp" to 10, "cycleMode" to 2, "remoteMode" to 4, "timeSpan" to 10).toString()) else emptyMap())
        tokenRequest(session, "/control/rc/remoteControl", data, time, vehicle.brand, vehicle.vin, expectData = false)
        return true // Cloud accepted the command; the physical result needs separate vehicle feedback.
    }

    private suspend fun tokenRequest(session: BydSession, path: String, data: JsonObject, time: String, brand: String,
        vin: String, idType: Int = 0, identifier: String = session.userId, expectData: Boolean = true): JsonObject {
        val response = request(path, BydProtocol.tokenRequest(device, data, session.username, identifier, idType,
            session.encryptToken, session.signToken, brand, time, vin), brand, "531")
        val payload = response.text("respondData")
        return if (payload.isEmpty() && !expectData) JsonObject(emptyMap())
            else Json.parseToJsonElement(BydProtocol.decrypt(payload, BydProtocol.digest(session.encryptToken))).jsonObject
    }

    private suspend fun request(path: String, plain: String, brand: String, version: String): JsonObject = withContext(Dispatchers.IO) {
        val response = client.post(BydProtocol.BASE_URL + path) {
            header("Content-Type", "application/json; charset=UTF-8"); header("accept-encoding", "identity")
            header("User-Agent", "okhttp/4.12.0"); header("version", version); header("platform", "ANDROID")
            header("BrandFlag", when (brand) { "2" -> "ocean"; "3" -> "denza"; "4" -> "yangwang"; "5" -> "fangchengbao"; else -> "dynasty" })
            setBody(BydProtocol.fields("request" to codec.b(plain)).toString())
        }
        check(response.status.value in 200..299) { "云端 HTTP ${response.status.value}" }
        val envelope = Json.parseToJsonElement(response.bodyAsText()).jsonObject.text("response")
        check(envelope.isNotEmpty()) { "云端响应格式不匹配" }
        val decoded = Json.parseToJsonElement(codec.a(envelope)).jsonObject
        if (decoded.text("code") != "0") throw BydCloudException(decoded.text("code"))
        decoded
    }

    private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.content.orEmpty()
}
