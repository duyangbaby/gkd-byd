package li.gkd.app.feature.vehicle

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import li.gkd.app.feature.vehicle.auto.BydDevice
import li.gkd.app.feature.vehicle.auto.BydProtocol
import li.gkd.app.feature.vehicle.auto.WbskCodec
import li.gkd.app.feature.vehicle.auto.WbskTables
import org.junit.Assert.*
import org.junit.Test

class BydProtocolTest {
    private fun codec() = WbskCodec().apply {
        f653a = File("src/main/assets/byd/wbsk_tables.json").inputStream().use(WbskTables::c)
    }

    @Test fun recoveredWbskWireOutputRemainsStableAndSupportsUnicodeAndBlockBoundaries() {
        val c = codec()
        // Fixed wire fixtures protect compatibility with the user-supplied 26.0928 codec.
        val fixtures = linkedMapOf(
            "" to "3a45c05961c1e11283057c4da770add18cdccd94cd60ccd47aeb52681b8bb3b9",
            "{\"probe\":\"gkd\"}" to "265afa1ec553b5d8b7ec7767a689f0b3a0574af6562228ff9b2f6aa1997ae1f5",
            "0123456789abcdef" to "0bd1543f5d8e65bd5e5d80c731ef22c28259086359d1e9b4f87cf179d7ddc907",
            "比亚迪" to "87031f561f235f736134ea122c135272fb1a6a7690f169bc932dbb2021cc7927",
        )
        fixtures.forEach { (plain, hash) ->
            val encrypted = c.b(plain)
            val actual = MessageDigest.getInstance("SHA-256").digest(encrypted.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(hash, actual)
            assertEquals(plain, c.a(encrypted))
        }
        for (length in listOf(1, 15, 17, 31, 32, 33, 1024)) {
            val plain = "a".repeat(length)
            assertEquals(plain, c.a(c.b(plain)))
        }
    }

    @Test fun loginUsesDoublePasswordMd5AndHasVerifiableOuterCheckcode() {
        val request = Json.parseToJsonElement(BydProtocol.login(BydDevice("device", "vivo", "model", 35),
            "test-user", "sample-password-not-real", "1", "1727697600000", "0".repeat(32))).jsonObject
        val inner = BydProtocol.decrypt(request.getValue("encryData").jsonPrimitive.content,
            BydProtocol.digest(BydProtocol.digest("sample-password-not-real")))
        val decoded = Json.parseToJsonElement(inner).jsonObject
        assertEquals(BydProtocol.digest("test-user"), decoded.getValue("imeiMD5").jsonPrimitive.content)
        assertEquals("VIVO", decoded.getValue("mobileBrand").jsonPrimitive.content)
        assertEquals("9.10.2", decoded.getValue("appVersion").jsonPrimitive.content)
        val withoutCheckcode = kotlinx.serialization.json.JsonObject(request.filterKeys { it != "checkcode" }).toString()
        val expected = MessageDigest.getInstance("SHA-256").digest(withoutCheckcode.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, request.getValue("checkcode").jsonPrimitive.content)
        assertEquals("BANGCLE01234", request.getValue("imei").jsonPrimitive.content)
    }
}
