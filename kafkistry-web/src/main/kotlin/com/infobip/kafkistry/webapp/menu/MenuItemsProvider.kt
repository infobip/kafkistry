package com.infobip.kafkistry.webapp.menu

interface MenuItemsProvider {

    fun provideMenuItems(): List<MenuItem>
}