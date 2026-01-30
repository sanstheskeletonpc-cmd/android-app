/*
 * Copyright (c) 2020 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.vpn

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import com.protonvpn.android.servers.Server
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.RecentsManager.RecentServersJsonAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.proton.core.util.kotlin.removeFirst
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RecentsManagerServerLegacyStorage(
    val serverId: String,
)

@Serializable
private data class RecentsManagerLegacyStorage(
    val recentCountries: List<String> = emptyList(),

    // Workaround for R8:
    // with R8 there is not enough info to deserialize the ArrayDeque items as Server objects and I can't figure out
    // rules to make it work.
    // As a workaround use an explicit deserializer. In the longer term we should move to storing recents in a DB.
    @JsonAdapter(RecentServersJsonAdapter::class)
    val recentServers: LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>> = LinkedHashMap(),
)

@Singleton
class RecentsManager @Inject constructor(
    mainScope: CoroutineScope,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    serverManager: ServerManager,
) {
    private val recentCountries = ArrayList<String>()

    // Country code -> Servers
    private val recentServers = LinkedHashMap<String, ArrayDeque<Server>>()

    val version = MutableStateFlow<Int>(0)

    init {
        mainScope.launch {
            serverManager.ensureLoaded()
            val loadedRecents =
                Storage.load(RecentsManager::class.java, RecentsManagerLegacyStorage::class.java)
            if (loadedRecents != null) {
                recentCountries.addAll(loadedRecents.recentCountries)
                recentServers.putAll(
                    loadedRecents.recentServers
                        .mapValues { (_, servers) ->
                            servers.mapNotNullTo(ArrayDeque()) { serverManager.getServerById(it.serverId) }
                        }
                        .filter { (_, servers) -> servers.isNotEmpty() }
                )
                version.update { it + 1 }
            }
        }

        mainScope.launch {
            vpnStatusProviderUI.status.collect { status ->
                if (status.state == VpnState.Connected) {
                    status.connectionParams?.let { params ->
                        addToRecentServers(params.server)
                        addToRecentCountries(params.server)
                        val persistedRecentServers = recentServers.mapValuesTo(LinkedHashMap()) { (_, servers) ->
                            servers.map { server ->
                                RecentsManagerServerLegacyStorage(serverId = server.serverId)
                            }
                        }
                        Storage.save(
                            RecentsManagerLegacyStorage(
                                recentCountries, persistedRecentServers
                            ),
                            RecentsManager::class.java
                        )
                        version.update { it + 1 }
                    }
                }
            }
        }
    }

    fun clear() {
        recentCountries.clear()
        recentServers.clear()
        Storage.delete(RecentsManager::class.java)
    }

    fun getRecentCountries(): List<String> = recentCountries

    private fun addToRecentServers(server: Server) {
        recentServers.getOrPut(server.exitCountry) {
            ArrayDeque(RECENT_SERVER_MAX_SIZE + 1)
        }.apply {
            removeFirst { it.serverName == server.serverName }
            addFirst(server)
            if (size > RECENT_SERVER_MAX_SIZE)
                removeAt(lastIndex)
        }
    }

    private fun addToRecentCountries(server: Server) {
        if (server.exitCountry.isNotEmpty()) {
            recentCountries.remove(server.exitCountry)
            if (recentCountries.size > RECENT_MAX_SIZE) {
                recentCountries.removeAt(recentCountries.lastIndex)
            }
            recentCountries.add(0, server.exitCountry)
        }
    }

    fun getRecentServers(country: String): List<Server>? = recentServers[country]

    fun getAllRecentServers(): List<Server> = recentServers.flatMap { (_, servers) -> servers }

    class RecentServersJsonAdapter : JsonDeserializer<LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>>>,
                                     JsonSerializer<LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>>>
    {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>> {
            if (json.isJsonObject) {
                val result = LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>>()
                val jsonMap = json.asJsonObject
                jsonMap.keySet().associateWithTo(result) { country ->
                    jsonMap.get(country).asJsonArray.asList().mapTo(ArrayList()) { jsonServer ->
                        context.deserialize(jsonServer, RecentsManagerServerLegacyStorage::class.java)
                    }
                }
                return result
            }
            return LinkedHashMap()
        }

        override fun serialize(
            src: LinkedHashMap<String, List<RecentsManagerServerLegacyStorage>>,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement = context.serialize(src)
    }

    companion object {
        const val RECENT_MAX_SIZE = 3
        const val RECENT_SERVER_MAX_SIZE = 3
    }
}
