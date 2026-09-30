package li.gkd.app.feature.vehicle.auto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import li.gkd.app.app
import li.gkd.app.feature.vehicle.VehicleService

@Serializable
private data class SavedBydAccount(val session: BydSession, val vehicles: List<BydVehicle> = emptyList(), val selectedVin: String = "")

data class BydAccountSummary(val nickname: String = "", val loggedIn: Boolean = false,
    val vehicles: List<BydVehicle> = emptyList(), val selectedVin: String = "")

object BydAccountStore {
    private const val ALIAS = "gkd_byd_account"
    private val prefs by lazy { app.getSharedPreferences("byd_account", 0) }
    private val json = Json { ignoreUnknownKeys = true }
    private var account: SavedBydAccount? = restore()
    val summary: StateFlow<BydAccountSummary>
        field = MutableStateFlow(snapshot())

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private fun restore(): SavedBydAccount? {
        val value = prefs.getString("encrypted", null) ?: return null
        return try {
            val parts = value.split('.')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            json.decodeFromString<SavedBydAccount>(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8))
        } catch (_: Exception) {
            prefs.edit().remove("encrypted").apply()
            null
        }
    }

    private fun save(value: SavedBydAccount?) {
        VehicleService.stop()
        val encoded = value?.let {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = cipher.doFinal(json.encodeToString(it).toByteArray())
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
        check(prefs.edit().putString("encrypted", encoded).commit()) { "无法保存账号" }
        account = value
        summary.value = snapshot()
    }

    private fun snapshot() = account?.let { BydAccountSummary(it.session.nickname, true, it.vehicles, it.selectedVin) } ?: BydAccountSummary()
    fun signIn(session: BydSession) = save(SavedBydAccount(session))
    fun updateVehicles(vehicles: List<BydVehicle>) {
        val current = checkNotNull(account)
        save(current.copy(vehicles = vehicles, selectedVin = current.selectedVin.takeIf { vin -> vehicles.any { it.vin == vin } }.orEmpty()))
    }
    fun select(vin: String) {
        val current = checkNotNull(account)
        require(current.vehicles.any { it.vin == vin })
        save(current.copy(selectedVin = vin))
    }
    fun session(): BydSession? = account?.session
    fun selectedVehicle(): BydVehicle? = account?.let { saved -> saved.vehicles.singleOrNull { it.vin == saved.selectedVin } }
    fun signOut() = save(null)
}
