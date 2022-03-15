/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2022 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest

import mu.KotlinLogging
import org.projectforge.business.user.UserPrefCache
import org.projectforge.business.user.UserXmlPreferencesCache
import org.projectforge.business.user.filter.CookieService
import org.projectforge.login.LoginService
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.RestResolver
import org.projectforge.ui.ResponseAction
import org.projectforge.ui.TargetType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val log = KotlinLogging.logger {}

/**
 * This rest service should be available without login (public).
 */
@RestController
@RequestMapping("${Rest.URL}/logout")
open class LogoutRest {
    @Autowired
    private lateinit var cookieService: CookieService

    @Autowired
    private lateinit var userXmlPreferencesCache: UserXmlPreferencesCache

    @Autowired
    private lateinit var userPrefCache: UserPrefCache

    @GetMapping
    fun logout(request: HttpServletRequest,
               response: HttpServletResponse)
            : ResponseAction {
        val user = LoginService.getUser(request)
        if (user != null) {
            userXmlPreferencesCache.flushToDB(user.id)
            userXmlPreferencesCache.clear(user.id)
            userPrefCache.flushToDB(user.id)
            userPrefCache.clear(user.id)
        }
        LoginService.logout(request)
        cookieService.clearAllCookies(request, response)
        if (user != null) {
            log.info("User successfully logged out: ${user.username}")
        }
        return ResponseAction(url = "/${RestResolver.REACT_PUBLIC_PATH}/login", targetType = TargetType.CHECK_AUTHENTICATION)
    }
}
