package com.cobblepalsworld.navigation

class NavigationBudget(private var remainingStarts: Int, private val globalStartBudget: (() -> Boolean)? = null) {
    fun tryConsumeStart(): Boolean {
        if (remainingStarts < 0) return true
        if (remainingStarts <= 0) return false
        if (globalStartBudget?.invoke() == false) return false
        remainingStarts--
        return true
    }
}