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
package com.protonvpn.android.models.profiles

import java.io.Serializable
import java.util.UUID

data class Profile @JvmOverloads constructor(
    val wrapper: ServerWrapper,
    val id: UUID? = UUID.randomUUID(),
) : Serializable {

    val isPreBakedProfile: Boolean
        get() = wrapper.isPreBakedProfile

    val country: String get() = wrapper.country

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Profile

        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
