package com.fullchee.questradecashoverlay.logins

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import kotlinx.serialization.json.Json

internal const val LOGINS_FILE = "logins.enc"
private const val KEYSET_NAME = "logins_keyset"
private const val KEYSET_PREFS = "logins_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://logins_master_key"

// Binds the ciphertext to this file, so a blob encrypted for another purpose never decrypts here.
private val ASSOCIATED_DATA = LOGINS_FILE.encodeToByteArray()

@Volatile
private var appLoginStore: LoginStore? = null

/** The app's one store: DataStore refuses two instances on the same file in a process. */
fun loginStore(context: Context): LoginStore = appLoginStore ?: synchronized(LoginStore::class) {
    appLoginStore ?: createLoginStore(context.applicationContext).also { appLoginStore = it }
}

private fun createLoginStore(context: Context): LoginStore {
    val dataStore = DataStoreFactory.create(
        serializer = EncryptedLoginsSerializer(keystoreAead(context)),
        produceFile = { context.dataStoreFile(LOGINS_FILE) }
    )
    return LoginStore(dataStore)
}

/** The keyset lives in shared preferences, wrapped by a key that never leaves the Android Keystore. */
private fun keystoreAead(context: Context): Aead {
    AeadConfig.register()
    return AndroidKeysetManager.Builder()
        .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()
        .keysetHandle
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}

internal class EncryptedLoginsSerializer(private val aead: Aead) : Serializer<StoredLogins> {
    override val defaultValue = StoredLogins()

    override suspend fun readFrom(input: InputStream): StoredLogins {
        val plaintext = try {
            aead.decrypt(input.readBytes(), ASSOCIATED_DATA)
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("The logins file doesn't decrypt with this install's key", e)
        }
        return Json.decodeFromString(plaintext.decodeToString())
    }

    override suspend fun writeTo(t: StoredLogins, output: OutputStream) {
        output.write(aead.encrypt(Json.encodeToString(t).encodeToByteArray(), ASSOCIATED_DATA))
    }
}
