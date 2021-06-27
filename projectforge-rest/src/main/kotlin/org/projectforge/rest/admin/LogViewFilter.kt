/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2021 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.rest.admin

import org.projectforge.common.anots.PropertyInfo
import org.projectforge.common.logging.LogFilter
import org.projectforge.common.logging.LogLevel

class LogViewFilter(
  @PropertyInfo(i18nKey = "system.admin.logViewer.level", required = true)
  var threshold: LogLevel = LogLevel.INFO,
  @PropertyInfo(i18nKey = "search")
  var search: String? = null,
  @PropertyInfo(i18nKey = "system.admin.logViewer.autoRefresh")
  var autoRefresh: Boolean? = null,
  val logSubscriptionId: Int? = null,
) {
  val logFilter
    get() = LogFilter(threshold = threshold, search = search)
}