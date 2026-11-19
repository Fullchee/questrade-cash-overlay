package com.fullchee.questradecashoverlay.logins

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.CorruptionException
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fullchee.questradecashoverlay.R
import com.fullchee.questradecashoverlay.questrade.Session
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class EncryptedLoginsTest {
    private val logins = StoredLogins(
        listOf(StoredLogin("id-1", "Mom", "refresh-secret", LoginStatus.NeedsReconnecting))
    )

    @Test
    fun `the serializer reads back what it wrote without the token in plain text`() = runTest {
        val serializer = EncryptedLoginsSerializer(newAead())

        val bytes = ByteArrayOutputStream().also { serializer.writeTo(logins, it) }.toByteArray()

        assertFalse(bytes.decodeToString().contains("refresh-secret"))
        assertEquals(logins, serializer.readFrom(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `a file encrypted with another key reads as corrupt`() = runTest {
        val bytes = ByteArrayOutputStream()
            .also { EncryptedLoginsSerializer(newAead()).writeTo(logins, it) }
            .toByteArray()

        assertThrows(CorruptionException::class.java) {
            runBlocking {
                EncryptedLoginsSerializer(newAead()).readFrom(ByteArrayInputStream(bytes))
            }
        }
    }

    @Test
    fun `the app's store is one instance that saves logins encrypted on disk`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = loginStore(context)
        val id = store.add("Mom", "refresh-secret")

        store.rotate(id) {
            Session("access", "https://api01.iq.questrade.com/".toHttpUrl(), "refresh-next")
        }

        assertSame(store, loginStore(context))
        assertEquals(listOf(Login(id, "Mom", LoginStatus.Ok)), store.logins.first())
        val onDisk = context.dataStoreFile(LOGINS_FILE).readBytes().decodeToString()
        assertFalse(onDisk.contains("refresh-next"))
        assertFalse(onDisk.contains("Mom"))
    }

    @Test
    fun `backups are off because a restored logins file can't decrypt without the Keystore key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun `device transfers and cloud backups skip the logins file and its keyset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val excluded = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                excluded += parser.getAttributeValue(null, "path")
            }
        }

        val bothFiles = listOf("datastore/$LOGINS_FILE", "logins_keyset_prefs.xml")
        assertEquals(bothFiles + bothFiles, excluded)
    }

    private fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
