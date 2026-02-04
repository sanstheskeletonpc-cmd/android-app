/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.servers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerAction
import com.protonvpn.android.appconfig.periodicupdates.toPeriodicActionResult
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.utils.AndroidUtils.registerBroadcastReceiver
import com.protonvpn.android.utils.DefaultLocaleProvider
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private val UpdateDelay = 4.days.inWholeMilliseconds

@Reusable
class UpdateServerTranslations @Inject constructor(
    @ApplicationContext appContext: Context,
    mainScope: CoroutineScope,
    private val api: dagger.Lazy<ProtonApiRetroFit>,
    private val translator: dagger.Lazy<Translator>,
    private val getLocale: dagger.Lazy<DefaultLocaleProvider>,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsInForeground inForeground: Flow<Boolean>
) {
    private val action = periodicUpdateManager.registerAction(
        "server_translations",
        ::updateTranslations,
        PeriodicUpdateSpec(UpdateDelay, setOf(inForeground))
    )

    init {
        mainScope.launch {
            delay(5.seconds)
            appContext.registerBroadcastReceiver(IntentFilter(Intent.ACTION_LOCALE_CHANGED)) {
                mainScope.launch { periodicUpdateManager.executeNow(action) }
            }
        }
    }

    private suspend fun updateTranslations(): PeriodicActionResult<ApiResult<*>> {
        val languageTag = getLocale.get().invoke().toLanguageTag()
        val result = api.get().getServerCities(languageTag)
        when (result) {
            is ApiResult.Success-> {
                ProtonLogger.logCustom(LogCategory.API, "Got server translations for ${result.value.languageCode}")
                translator.get().updateTranslations(result.value.cities, result.value.states)
            }

            else -> {
                ProtonLogger.logCustom(LogCategory.API, "Failed to update server translations: $result")
            }
        }
        return result.toPeriodicActionResult()
    }
}