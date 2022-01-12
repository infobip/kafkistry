package com.infobip.kafkistry.webapp.menu

data class MenuItem(
        val id: String,
        val name: String,
        val urlPath: String,
        val newItem: Boolean
)