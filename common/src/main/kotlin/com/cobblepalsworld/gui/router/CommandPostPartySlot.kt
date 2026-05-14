/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblepalsworld.gui.router

internal object CommandPostPartySlot {
    data class Bounds(val left: Int, val top: Int, val size: Int = CommandPostPartyWidget.SLOT_SIZE)

    fun bounds(partyIndex: Int): Bounds? {
        if (partyIndex !in 0..5) return null
        return Bounds(CommandPostPartyWidget.slotLeft(partyIndex), CommandPostPartyWidget.slotTop(partyIndex))
    }
}