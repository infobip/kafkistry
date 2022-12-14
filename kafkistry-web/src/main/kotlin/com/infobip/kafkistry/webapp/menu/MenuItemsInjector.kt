package com.infobip.kafkistry.webapp.menu

import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import java.util.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class MenuItemsInjector(
        menuItemsProviders: Optional<List<MenuItemsProvider>>
) : HandlerInterceptor {

    private val menuItems: List<MenuItem> = menuItemsProviders.orElse(emptyList())
            .map { it.provideMenuItems() }
            .flatten()

    override fun postHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any, modelAndView: ModelAndView?) {
        if (modelAndView == null) {
            return
        }
        injectMenuItems(modelAndView)
    }

    fun injectMenuItems(modelAndView: ModelAndView) {
        modelAndView.addObject("menuItems", menuItems)
    }
}
