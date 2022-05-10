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

package org.projectforge.plugins.datatransfer.rest

import org.projectforge.framework.i18n.translate
import org.projectforge.framework.jcr.Attachment
import org.projectforge.framework.jcr.AttachmentsAccessChecker
import org.projectforge.framework.jcr.AttachmentsEventType
import org.projectforge.framework.jcr.AttachmentsService
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.jcr.FileInfo
import org.projectforge.plugins.datatransfer.DataTransferAreaDO
import org.projectforge.plugins.datatransfer.NotificationMailService
import org.projectforge.rest.AttachmentsRestUtils
import javax.servlet.http.HttpServletResponse

object DataTransferRestUtils {
  fun downloadAll(
    response: HttpServletResponse,
    attachmentsService: AttachmentsService,
    attachmentsAccessChecker: AttachmentsAccessChecker,
    notificationMailService: NotificationMailService,
    dbObj: DataTransferAreaDO,
    areaName: String?,
    jcrPath: String,
    id: Int,
    attachments: List<Attachment>? = null,
    byUser: PFUserDO? = null,
    byExternalUser: String? = null,
  ) {
    AttachmentsRestUtils.downloadAll(
      response,
      attachmentsService,
      attachmentsAccessChecker,
      areaName,
      jcrPath,
      id,
      attachments,
    )
    notificationMailService.sendMail(
      AttachmentsEventType.DOWNLOAD_ALL,
      FileInfo(translate("plugins.datatransfer.mail.action.DOWNLOAD_ALL.filename")),
      dbObj,
      byUser = byUser,
      byExternalUser = byExternalUser,
    )
  }
}
