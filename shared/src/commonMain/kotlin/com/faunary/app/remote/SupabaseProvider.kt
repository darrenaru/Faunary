package com.faunary.app.remote

import com.faunary.app.FaunaryConfig
import com.faunary.app.data.SettingsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val PHOTO_BUCKET = "sighting-photos"

/**
 * Owns the Supabase client. When no URL/key is configured the app keeps working fully offline:
 * [client] is null and every online feature quietly turns itself off.
 */
class SupabaseProvider(
    private val settings: SettingsRepository,
) {
    val isConfigured: Boolean = FaunaryConfig.SUPABASE_URL.isNotBlank() && FaunaryConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) null
        else createSupabaseClient(FaunaryConfig.SUPABASE_URL, FaunaryConfig.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }

    private val signInLock = Mutex()

    /** Returns the current user id, signing in anonymously on first use. Null when offline/unconfigured. */
    suspend fun ensureUserId(): String? {
        val c = client ?: return null
        return signInLock.withLock {
            runCatching {
                c.auth.awaitInitialization()
                c.auth.currentUserOrNull()?.id ?: run {
                    c.auth.signInAnonymously()
                    c.auth.currentUserOrNull()?.id?.also { syncProfile(it) }
                }
            }.getOrNull()
        }
    }

    fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id

    /** Publishes the explorer name so other users see who found what. */
    suspend fun syncProfile(userId: String? = null) {
        val c = client ?: return
        val uid = userId ?: ensureUserId() ?: return
        runCatching {
            c.from("profiles").upsert(ProfileDto(uid, settings.settings.value.explorerName))
        }
    }
}
