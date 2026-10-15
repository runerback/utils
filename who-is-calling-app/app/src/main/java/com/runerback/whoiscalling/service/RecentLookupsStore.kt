package com.runerback.whoiscalling.service

import com.runerback.whoiscalling.data.api.LookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class RecentLookup(
    val number: String,
    val result: LookupResult?,
    val blocked: Boolean,
    val error: String?,
    val timestamp: Long,
)

@Singleton
class RecentLookupsStore @Inject constructor() {
    private val _lookups = MutableStateFlow<List<RecentLookup>>(emptyList())
    val lookups: StateFlow<List<RecentLookup>> = _lookups.asStateFlow()

    fun add(number: String, result: LookupResult?, blocked: Boolean, error: String?) {
        val entry = RecentLookup(
            number = number,
            result = result,
            blocked = blocked,
            error = error,
            timestamp = System.currentTimeMillis(),
        )
        _lookups.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
