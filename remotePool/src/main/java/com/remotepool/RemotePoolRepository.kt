package com.remotepool

import android.util.Base64
import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.remotepool.exceptions.RemoteAuthException
import com.remotepool.exceptions.RemoteHttpException
import com.remotepool.models.AuthUser
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.HttpException
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken


// KeyStone
private data class KsDomain(val name: String)
private data class KsUser(val name: String, val domain: KsDomain, val password: String)
private data class KsPassword(val user: KsUser)
private data class KsIdentity(val methods: List<String>, val password: KsPassword)
private data class KsProject(val name: String, val domain: KsDomain)
private data class KsScope(val project: KsProject)
private data class KsAuth(val identity: KsIdentity, val scope: KsScope)
private data class KsRequest(val auth: KsAuth)


private interface IdentityApi {
    @POST("identity/v3/auth/tokens")
    suspend fun auth(@Body body: KsRequest): Response<ResponseBody>
}

private interface SecretsApi {
    @GET("secrets-manager/v1/")
    suspend fun list(
        @Header("X-Auth-Token") token: String,
        @Query("list") list: String = "true"
    ): JsonElement

    @GET("secrets-manager/v1/{key}")
    suspend fun get(
        @Header("X-Auth-Token") token: String,
        @Path("key") key: String
    ): ResponseBody
}

data class VersionSecret(
    @SerializedName("version_id") val versionId: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("is_current") val isCurrent: Boolean,
    val value: String
)

data class ResponseSecret(
    val name: String,
    val description: String,
    val version : VersionSecret,
    @SerializedName("created_at") val createdAt : String
)

class RemotePoolRepository() {

    private companion object {
        const val BASE_URL = "https://cloud.api.selcloud.ru/"
        const val DEFAULT_PREFIX = "AWGConfig"

        /** <Prefix><Number> format **/
        fun buildPattern(prefix: String) =
            Regex("^${Regex.escape(prefix)}(\\d+)$")
    }

    @Volatile
    private var pattern: Regex = buildPattern(DEFAULT_PREFIX)

    private val retrofit: Retrofit by lazy {

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val identityApi: IdentityApi by lazy { retrofit.create(IdentityApi::class.java) }
    private val secretsApi: SecretsApi by lazy { retrofit.create(SecretsApi::class.java) }

    fun setSearchedFormat(searchedPrefix: String) {
        pattern = buildPattern(searchedPrefix)
    }

    suspend fun getToken(credentials: AuthUser, password: String): String {

        val domain = KsDomain(credentials.accountId)
        val request = KsRequest(
            KsAuth(
                identity = KsIdentity(
                    methods = listOf("password"),
                    password = KsPassword(KsUser(credentials.username, domain, password))
                ),
                scope = KsScope(
                    project=KsProject(credentials.projectName, domain)
                )
            )
        )
        val response = identityApi.auth(request)
        if (!response.isSuccessful) throw httpError(response.code())
        return response.headers()["X-Subject-Token"]
            ?: throw IOException("Selectel auth: X-Subject-Token header is missing")
    }

    private data class Entry(val key: String, val number: Long)

    private suspend fun fetchEntries(token: String): List<Entry> {
        val p = pattern
        return extractKeys(secretsApi.list(token)).mapNotNull { key ->
            val m = p.matchEntire(key) ?: return@mapNotNull null
            val number = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            Entry(key, number)
        }
    }

    suspend fun getAllConfigs(token: String): List<String> = remote {
        fetchEntries(token)
            .sortedBy { it.number }
            .map { it.key }
    }

    suspend fun getLastConfig(token: String): String? = remote {
        val last = fetchEntries(token).maxByOrNull { it.number } ?: return@remote null
        val serializedResponse = secretsApi.get(token, last.key).use { it.string() }
        val responseObject = Gson().fromJson(serializedResponse, ResponseSecret::class.java)
        String(Base64.decode(responseObject.version.value, Base64.DEFAULT), Charsets.UTF_8)

    }

    private fun httpError(code: Int): IOException =
        if (code == 401 || code == 403) RemoteAuthException(code) else RemoteHttpException(code)

    /** Retrofit throws HttpException for non-2xx; callers only know our own typed errors. */
    private suspend fun <T> remote(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw httpError(e.code())
    }

    private fun extractKeys(root: JsonElement): List<String> {
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.entrySet()
                .firstOrNull { it.value.isJsonArray }?.value?.asJsonArray
            else -> null
        } ?: return emptyList()

        return array.mapNotNull { el ->
            when {
                el.isJsonPrimitive -> el.asString
                el.isJsonObject -> el.asJsonObject.let { o ->
                    (o.get("key") ?: o.get("name"))?.takeIf { it.isJsonPrimitive }?.asString
                }
                else -> null
            }
        }
    }
}