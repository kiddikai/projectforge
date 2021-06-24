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

package org.projectforge.plugins.merlin.rest

import mu.KotlinLogging
import org.projectforge.business.group.service.GroupService
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.plugins.merlin.*
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.rest.core.RestButtonEvent
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.Group
import org.projectforge.rest.dto.PostData
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.annotation.PostConstruct
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("${Rest.URL}/merlin")
class MerlinPagesRest :
  AbstractDTOPagesRest<MerlinTemplateDO, MerlinTemplate, MerlinTemplateDao>(
    MerlinTemplateDao::class.java,
    "plugins.merlin.title"
  ) {
  @Autowired
  private lateinit var groupService: GroupService

  @Autowired
  private lateinit var merlinRunner: MerlinRunner

  @Autowired
  private lateinit var userService: UserService

  @PostConstruct
  private fun postConstruct() {
    enableJcr()
    instance = this
    merlinRunner.attachmentsAccessChecker = attachmentsAccessChecker
    merlinRunner.jcrPath = jcrPath!!
  }

  /**
   * Initializes new toDos for adding.
   */
  override fun newBaseDO(request: HttpServletRequest?): MerlinTemplateDO {
    val template = super.newBaseDO(request)
    template.adminIds = "${ThreadLocalUserContext.getUserId()}"
    template.fileNamePattern = "\${date}-document"
    return template
  }

  override fun transformForDB(dto: MerlinTemplate): MerlinTemplateDO {
    val obj = MerlinTemplateDO()
    dto.copyTo(obj)
    return obj
  }

  override fun transformFromDB(obj: MerlinTemplateDO, editMode: Boolean): MerlinTemplate {
    val dto = MerlinTemplate()
    dto.copyFrom(obj)

    // Group names needed by React client (for ReactSelect):
    Group.restoreDisplayNames(dto.accessGroups, groupService)

    // Usernames needed by React client (for ReactSelect):
    User.restoreDisplayNames(dto.admins, userService)
    User.restoreDisplayNames(dto.accessUsers, userService)

    dto.adminsAsString = dto.admins?.joinToString { it.displayName ?: "???" } ?: ""
    dto.accessGroupsAsString = dto.accessGroups?.joinToString { it.displayName ?: "???" } ?: ""
    dto.accessUsersAsString = dto.accessUsers?.joinToString { it.displayName ?: "???" } ?: ""

    obj.id?.let { id ->
      val list =
        attachmentsService.getAttachments(jcrPath!!, id, attachmentsAccessChecker)?.sortedByDescending { it.created }
      dto.wordTemplateFileName = list?.find { it.fileExtension == "docx" }?.name
      dto.excelTemplateDefinitionFileName = list?.find { it.fileExtension == "xlsx" }?.name
    }
    return dto
  }

  /**
   * LAYOUT List page
   */
  override fun createListLayout(): UILayout {
    val layout = super.createListLayout()
      .add(
        UITable.createUIResultSetTable()
          .add(lc, "created", "modified", "name", "description")
          .add(UITableColumn("attachmentsSizeFormatted", titleIcon = UIIconType.PAPER_CLIP))
          .add(UITableColumn("adminsAsString", "plugins.merlin.admins"))
          .add(UITableColumn("accessUsersAsString", "plugins.merlin.accessUsers"))
          .add(UITableColumn("accessGroupsAsString", "plugins.merlin.accessGroups"))
      )
      .add(
        UIAlert(
          "'For further documentation, please refer: [Merlin documentation](https://github.com/micromata/Merlin/blob/master/docs/templates.adoc)",
          markdown = true
        )
      )
      .add(
        UIAlert(
          """'# TODO
* Listener of log files for scanning and executing Merlin stuff
* Writing to inboxes
* Demo templates for creating (menu entry in list view)""",
          color = UIColor.WARNING,
          markdown = true
        )
      )
    return LayoutUtils.processListPage(layout, this)
  }

  /**
   * @return the data transfer view page.
   */
  override fun getStandardEditPage(): String {
    return "${PagesResolver.getDynamicPageUrl(MerlinExecutionPageRest::class.java)}:id"
  }

  @PostMapping("exportExcelTemplate")
  fun exportExcelTemplate(@Valid @RequestBody postData: PostData<MerlinTemplate>): ResponseEntity<*> {
    val dto = postData.data
    val workbook = merlinRunner.writeTemplateDefinitionWorkbook(dto)
    val filename = workbook.first
    val bytes = workbook.second
    return RestUtils.downloadFile(filename, bytes)
  }


  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: MerlinTemplate, userAccess: UILayout.UserAccess): UILayout {
    return updateLayoutAndData(null, dto, userAccess).first
  }

  override fun afterOperationRedirectTo(
    obj: MerlinTemplateDO,
    postData: PostData<MerlinTemplate>,
    event: RestButtonEvent
  ): String? {
    return if (event == RestButtonEvent.SAVE || event == RestButtonEvent.UPDATE) PagesResolver.getDynamicPageUrl(
      MerlinExecutionPageRest::class.java,
      id = obj.id,
      absolute = true
    ) else null
  }


  private fun getUserAccess(dbo: MerlinTemplateDO): UILayout.UserAccess {
    val userAccess = UILayout.UserAccess()
    super.checkUserAccess(dbo, userAccess)
    return userAccess
  }

  private fun createEditLayoutSuper(dto: MerlinTemplate, userAccess: UILayout.UserAccess): UILayout {
    return super.createEditLayout(dto, userAccess)
  }

  companion object {
    private lateinit var instance: MerlinPagesRest

    private val merlinRunner: MerlinRunner
      get() = instance.merlinRunner

    internal fun updateLayoutAndData(
      dbo: MerlinTemplateDO? = null,
      dto: MerlinTemplate = instance.transformFromDB(dbo!!),
      userAccess: UILayout.UserAccess? = null
    ): Pair<UILayout, MerlinTemplate> {
      MerlinPlugin.ensureUserLogSubscription()
      check(dbo != null || userAccess != null) { "dbo or userAcess must be given." }
      val id = dbo?.id ?: dto.id
      val stats = if (id != null) {
        merlinRunner.getStatistics(id)
      } else {
        MerlinStatistics()
      }
      val inputVariables = UIBadgeList()
      stats.variables.sortedBy { it.name.toLowerCase() }.forEach {
        if (it.input) {
          inputVariables.add(UIBadge(it.name, it.uiColor))
        }
      }
      val dependentVariables = UIBadgeList()
      stats.variables.sortedBy { it.name.toLowerCase() }.forEach {
        if (it.dependent) {
          dependentVariables.add(UIBadge(it.name, it.uiColor))
        }
      }
      val lc = LayoutContext(MerlinTemplateDO::class.java)
      val adminsSelect = UISelect.createUserSelect(
        lc,
        "admins",
        true,
        "plugins.merlin.admins",
        tooltip = "plugins.merlin.admins.info"
      )
      val accessUsers = UISelect.createUserSelect(
        lc,
        "accessUsers",
        true,
        "plugins.merlin.accessUsers",
        tooltip = "plugins.merlin.accessUsers.info"
      )
      val accessGroups = UISelect.createGroupSelect(
        lc,
        "accessGroups",
        true,
        "plugins.merlin.accessGroups",
        tooltip = "plugins.merlin.accessGroups.info"
      )
      val layout = instance.createEditLayoutSuper(dto, userAccess ?: instance.getUserAccess(dbo!!))
      val fieldset = UIFieldset(md = 12, lg = 12)
        .add(lc, "name")
        .add(
          UIRow()
            .add(
              UICol(md = 6)
                .add(UIInput("fileNamePattern", lc))
            )
            .add(
              UICol(md = 6)
                .add(lc, "stronglyRestrictedFilenames")
            )
        )
        .add(lc, "description")
        .add(
          UIRow()
            .add(
              UICol(md = 6)
                .add(
                  UIReadOnlyField(
                    "wordTemplateFileName",
                    label = "plugins.merlin.wordTemplateFile",
                    tooltip = "plugins.merlin.wordTemplateFile.info"
                  )
                )
                .add(
                  UIReadOnlyField(
                    "excelTemplateDefinitionFileName",
                    label = "plugins.merlin.templateConfigurationFile",
                    tooltip = "plugins.merlin.templateConfigurationFile.info"
                  )
                )
            )
            .add(
              UICol(md = 6)
                .add(UILabel("plugins.merlin.variables.input", tooltip = "plugins.merlin.variables.input.info"))
                .add(inputVariables)
                .add(
                  UILabel(
                    "plugins.merlin.variables.dependant",
                    tooltip = "plugins.merlin.variables.dependant.info"
                  )
                )
                .add(dependentVariables)
            )
        )
      fieldset
        .add(
          UIRow()
            .add(
              UICol(collapseTitle = translate("plugins.merlin.variables.input"))
                .add(createVariableTable())
            )
        )
        .add(
          UIRow()
            .add(
              UICol(collapseTitle = translate("plugins.merlin.variables.dependant"))
                .add(createDependenVariableTable())
            )
        )
      if (!stats.conditionals.isNullOrEmpty()) {
        fieldset
          .add(
            UIRow()
              .add(
                UICol(collapseTitle = translate("plugins.merlin.variables.conditionals"))
                  .add(UIAlert(message = "'${stats.conditionalsAsMarkdown()}", markdown = true, color = UIColor.LIGHT))
              )
          )
      }

      layout.add(fieldset)
        .add(
          UIFieldset(md = 12, lg = 12, title = "access.title.heading")
            .add(
              UIRow()
                .add(
                  UICol(md = 4)
                    .add(adminsSelect)
                )
                .add(
                  UICol(md = 4)
                    .add(accessUsers)
                )
                .add(
                  UICol(md = 4)
                    .add(accessGroups)
                )
            )
        )
        .add(
          UIFieldset(title = "attachment.list")
            .add(UIAttachmentList(instance.category, dto.id))
        )
      layout.add(
        UIButton(
          "exportExcelTemplate",
          title = translate("plugins.merlin.exportExcelTemplate"),
          tooltip = "plugins.merlin.exportExcelTemplate.info",
          color = UIColor.LINK,
          responseAction = ResponseAction(
            RestResolver.getRestUrl(
              instance.javaClass,
              "exportExcelTemplate"
            ), targetType = TargetType.POST
          )
        )
      )

      dto.wordTemplateFileName = stats.wordTemplateFilename
      dto.excelTemplateDefinitionFileName = stats.excelTemplateDefinitionFilename
      dto.variables = stats.variables.filter { it.defined && !it.dependent }.toMutableList()
      dto.dependentVariables = stats.variables.filter { it.dependent }.sortedBy { it.name.toLowerCase() }.toMutableList()
      return Pair(LayoutUtils.processEditPage(layout, dto, instance), dto)
    }

    private fun createVariableTable(): UITable {
      return UITable("variables")
        .add(UITableColumn("name", title = "plugins.merlin.variable.name", sortable = false))
        .add(UITableColumn("type", title = "plugins.merlin.variable.type", sortable = false))
        .add(
          UITableColumn(
            "required",
            title = "plugins.merlin.variable.required",
            sortable = false
          ).setStandardBoolean()
        )
        .add(
          UITableColumn(
            "unique",
            title = "plugins.merlin.variable.unique",
            tooltip = "plugins.merlin.variable.unique",
            sortable = false
          ).setStandardBoolean()
        )
        .add(
          UITableColumn(
            "allowedValuesFormatted",
            title = "plugins.merlin.variable.allowedValues",
            sortable = false
          )
        )
        .add(
          UITableColumn(
            "description",
            title = "description",
            sortable = false
          )
        )
    }

    private fun createDependenVariableTable(): UITable {
      return UITable("dependentVariables")
        .add(UITableColumn("name", title = "plugins.merlin.variable.name", sortable = false))
        .add(UITableColumn("dependsOn", title = "plugins.merlin.variable.dependsOn", sortable = false))
        .add(
          UITableColumn(
            "mappingFormatted",
            title = "plugins.merlin.variable.mapping",
            sortable = false
          )
        )
    }
  }
}
