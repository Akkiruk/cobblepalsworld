package com.cobblepalsworld.navigation

class NavigationBudget(private var remainingStarts: Int) {
    fun tryConsumeStart(): Boolean {
        if (remainingStarts < 0) return true
        if (remainingStarts <= 0) return false
        remainingStarts--
        return true
    }
}