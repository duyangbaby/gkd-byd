package li.gkd.app.feature.vehicle.auto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class BydDevice(val device: String, val manufacturer: String, val model: String, val sdk: Int)

/** Field order, uppercase MD5, zero IV and unusual SHA-1 formatting match the recovered protocol. */
object BydProtocol {
    const val BASE_URL = "https://dilinksuperappserver-cn.byd.auto"
    fun digest(value: String, algorithm: String = "MD5", uppercase: Boolean = true): String =
        MessageDigest.getInstance(algorithm).digest(value.toByteArray()).joinToString("") {
            String.format(Locale.ROOT, if (uppercase) "%02X" else "%02x", it.toInt() and 255)
        }

    fun signature(data: JsonObject, key: String): String {
        val plain = data.entries.sortedBy { it.key }.joinToString("&") { (name, value) ->
            "$name=${if (value is JsonPrimitive) value.content else value.toString()}"
        } + "&password=$key"
        return MessageDigest.getInstance("SHA-1").digest(plain.toByteArray()).mapIndexed { index, byte ->
            String.format(Locale.ROOT, if (index % 2 == 0) "%02X" else "%02x", byte.toInt() and 255).removePrefix("0")
        }.joinToString("")
    }

    fun encrypt(plain: String, key: String): String = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(hex(key), "AES"), IvParameterSpec(ByteArray(16)))
        doFinal(plain.toByteArray()).joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
    }

    fun decrypt(ciphertext: String, key: String): String = Cipher.getInstance("AES/CBC/PKCS5Padding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(hex(key), "AES"), IvParameterSpec(ByteArray(16)))
        doFinal(hex(ciphertext)).toString(Charsets.UTF_8)
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun random() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }

    fun fields(vararg values: Pair<String, Any?>) = JsonObject(linkedMapOf<String, JsonElement>().apply {
        values.forEach { (name, value) -> put(name, when (value) {
            null -> JsonNull
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }) }
    })

    fun login(device: BydDevice, username: String, password: String, brand: String, time: String, random: String): String {
        val imei = digest(username)
        val data = fields("appInnerVersion" to "502", "appVersion" to "9.10.2", "bluetoothMac" to "", "city" to "",
            "configVersion" to "10000", "deviceType" to "0", "devicename" to device.device.uppercase(Locale.ROOT),
            "imeiMD5" to imei, "isAuto" to "0", "latitude" to "", "longitude" to "", "mobileBrand" to device.manufacturer.uppercase(Locale.ROOT),
            "mobileModel" to device.model, "networkOperator" to "无", "networkType" to "wifi", "osType" to "Android",
            "osVersion" to device.sdk.toString(), "random" to random, "softType" to "0", "timeStamp" to time)
        val signData = JsonObject(data + fields("appChannel" to "99", "identifier" to username, "loginType" to 0,
            "reqTimestamp" to time, "targetBrand" to brand))
        val outer = fields("appChannel" to "99", "encryData" to encrypt(data.toString(), digest(digest(password))),
            "identifier" to username, "imeiMD5" to imei, "isAuto" to "0", "loginType" to 0, "reqTimestamp" to time,
            "sign" to signature(signData, digest(password)), "targetBrand" to brand)
        return envelope(device, outer, time)
    }

    fun tokenRequest(device: BydDevice, data: JsonObject, username: String, identifier: String, identifierType: Int,
        encryptToken: String, signToken: String, brand: String, time: String, vin: String = ""): String {
        val signData = JsonObject(data + fields("appChannel" to "99", "identifier" to identifier, "identifierType" to identifierType,
            "imeiMD5" to digest(username)) + (if (vin.isEmpty()) emptyMap() else fields("objective" to vin)) +
            fields("reqTimestamp" to time, "targetBrand" to brand, "vehicleBrand" to brand))
        val outer = fields("appChannel" to "99", "encryData" to encrypt(data.toString(), digest(encryptToken)),
            "identifier" to identifier, "identifierType" to identifierType, "imeiMD5" to digest(username),
            "objective" to if (vin.isEmpty()) null else vin, "outModelTypes" to null, "reqTimestamp" to time,
            "sign" to signature(signData, digest(signToken)), "softType" to null, "targetBrand" to brand,
            "vehicleBrand" to brand, "version" to null)
        return envelope(device, outer, time)
    }

    fun deviceFields(device: BydDevice, username: String, time: String, random: String) = fields(
        "deviceName" to device.device.uppercase(Locale.ROOT), "deviceType" to "0", "imeiMD5" to digest(username),
        "mobileBrand" to device.manufacturer.uppercase(Locale.ROOT), "mobileModel" to device.model, "networkOperator" to "无",
        "networkType" to "wifi", "osType" to "Android", "osVersion" to device.sdk.toString(), "random" to random,
        "softType" to "0", "timeStamp" to time, "version" to "531", "appUiName" to "")

    private fun envelope(device: BydDevice, outer: JsonObject, time: String): String {
        val base = JsonObject(outer + fields("ostype" to "and", "imei" to "BANGCLE01234", "mac" to "00:00:00:00:00:00",
            "model" to device.model, "sdk" to device.sdk.toString(), "mod" to device.manufacturer, "serviceTime" to time))
        return JsonObject(base + fields("checkcode" to digest(base.toString(), "SHA-256", false))).toString()
    }
}
