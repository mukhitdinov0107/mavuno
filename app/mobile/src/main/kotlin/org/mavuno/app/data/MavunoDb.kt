package org.mavuno.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.mavuno.app.services
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SyncState {
    const val LOCAL_ONLY = "local_only"
    const val QUEUED = "queued"
    const val SENT = "sent"
}

@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val recordId: String,
    val createdAt: Long,
    val status: String,
    val topCause: String?,
    /** The full field record (PRD 7.6) as JSON. */
    val json: String,
    val shareRecord: Boolean,
    val sharePhotos: Boolean,
    val syncState: String,
    val attempts: Int = 0,
    val reply: String? = null,
)

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val photoId: String,
    val recordId: String,
    val path: String,
    val treeIndex: Int,
    val leafClass: String?,
    val prob: Double?,
    val accepted: Boolean,
)

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: RecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("SELECT * FROM records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE recordId = :id")
    suspend fun byId(id: String): RecordEntity?

    @Query("SELECT * FROM records WHERE syncState = 'queued' ORDER BY createdAt")
    suspend fun queued(): List<RecordEntity>

    @Query("SELECT COUNT(*) FROM records WHERE syncState = 'queued'")
    fun observeQueuedCount(): Flow<Int>

    @Query("SELECT recordId FROM records WHERE syncState = 'sent' AND reply IS NULL")
    suspend fun sentAwaitingReply(): List<String>

    @Query("UPDATE records SET syncState = 'sent' WHERE recordId = :id")
    suspend fun markSent(id: String)

    @Query("UPDATE records SET attempts = attempts + 1 WHERE recordId = :id")
    suspend fun bumpAttempts(id: String)

    @Query("UPDATE records SET reply = :reply WHERE recordId = :id")
    suspend fun setReply(id: String, reply: String)

    @Query("SELECT * FROM photos WHERE recordId = :recordId")
    suspend fun photosFor(recordId: String): List<PhotoEntity>
}

@Database(entities = [RecordEntity::class, PhotoEntity::class], version = 1, exportSchema = false)
abstract class MavunoDb : RoomDatabase() {
    abstract fun records(): RecordDao

    companion object {
        private const val NAME = "mavuno.db"

        fun open(context: Context): MavunoDb {
            System.loadLibrary("sqlcipher")
            val passphrase = DbKey.passphrase(context)
            return Room.databaseBuilder(context, MavunoDb::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }

        fun deleteFiles(context: Context) {
            context.deleteDatabase(NAME)
            DbKey.destroy(context)
        }
    }
}

/**
 * The SQLCipher passphrase is random, wrapped with an AES key held in the Android Keystore,
 * and stored wrapped in app prefs. Deleting the Keystore key makes any leftover database unreadable.
 */
private object DbKey {
    private const val ALIAS = "mavuno_db_wrap"

    fun passphrase(context: Context): ByteArray {
        val prefs = context.services.prefs
        prefs.wrappedDbKey?.let { return unwrap(it) }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.wrappedDbKey = wrap(fresh)
        return fresh
    }

    fun destroy(context: Context) {
        context.services.prefs.wrappedDbKey = null
        keyStore().deleteEntry(ALIAS)
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun wrapKey(): SecretKey {
        (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, wrapKey()) }
        return b64(cipher.iv) + ":" + b64(cipher.doFinal(plain))
    }

    private fun unwrap(stored: String): ByteArray {
        val (iv, data) = stored.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(data)
    }

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
