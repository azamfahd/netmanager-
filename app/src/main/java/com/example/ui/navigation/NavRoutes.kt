package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    data object Dashboard : Screen

    @Serializable
    data object Finance : Screen

    @Serializable
    data object ActiveUsers : Screen

    @Serializable
    data object Nodes : Screen

    @Serializable
    data object Cards : Screen

    @Serializable
    data object POSAgents : Screen

    @Serializable
    data object Profiles : Screen

    @Serializable
    data object Tools : Screen

    @Serializable
    data object SystemEventLog : Screen

    @Serializable
    data object AdminAudit : Screen

    @Serializable
    data object VoucherPrint : Screen

    @Serializable
    data class UserSessionDetail(val username: String) : Screen
}
