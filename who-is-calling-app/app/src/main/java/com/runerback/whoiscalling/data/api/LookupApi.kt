package com.runerback.whoiscalling.data.api

import com.runerback.whoiscalling.data.settings.LookupSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LookupApi @Inject constructor(
    private val client: HttpClient,
) {
    suspend fun lookup(urlTemplate: String, number: String, timeoutMillis: Long): Result<LookupResult> =
        runCatching {
            require(urlTemplate.contains(LookupSettings.NUMBER_PLACEHOLDER)) {
                "URL template must contain ${LookupSettings.NUMBER_PLACEHOLDER}"
            }
            val url = urlTemplate.replace(LookupSettings.NUMBER_PLACEHOLDER, number.encodeURLParameter())
            val response = client.get(url) {
                timeout { requestTimeoutMillis = timeoutMillis }
                accept(ContentType.Application.Json)
            }
            check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
            response.body<LookupResult>()
        }
}
