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
import org.projectforge.SystemStatus
import org.projectforge.business.ldap.LdapUserDao
import org.projectforge.business.login.Login
import org.projectforge.business.user.UserDao
import org.projectforge.business.user.UserLocale
import org.projectforge.framework.access.AccessChecker
import org.projectforge.framework.configuration.Configuration
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.api.MagicFilter
import org.projectforge.framework.persistence.api.QueryFilter
import org.projectforge.framework.persistence.api.impl.CustomResultFilter
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.time.TimeNotation
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.dto.PostData
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.projectforge.ui.filter.UIFilterListElement
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

private val log = KotlinLogging.logger {}

@RestController
@RequestMapping("${Rest.URL}/user")
class UserPagesRest
  : AbstractDTOPagesRest<PFUserDO, User, UserDao>(UserDao::class.java, "user.title") {

  @Autowired
  private lateinit var accessChecker: AccessChecker

  @Autowired
  private lateinit var ldapUserDao: LdapUserDao

  override fun transformFromDB(obj: PFUserDO, editMode: Boolean): User {
    val user = User()
    val copy = PFUserDO.createCopyWithoutSecretFields(obj)
    if (copy != null) {
      user.copyFrom(copy)
    }
    return user
  }

  override fun transformForDB(dto: User): PFUserDO {
    val userDO = PFUserDO()
    dto.copyTo(userDO)
    return userDO
  }

  @Autowired
  private lateinit var userDao: UserDao

  override val classicsLinkListUrl: String
    get() = "wa/userList"


  /**
   * LAYOUT List page
   */
  override fun createListLayout(
    request: HttpServletRequest,
    layout: UILayout,
    magicFilter: MagicFilter,
    userAccess: UILayout.UserAccess
  ) {
    userAccess.update = accessChecker.isLoggedInUserMemberOfAdminGroup
    val agGrid = agGridSupport.prepareUIGrid4ListPage(
      request,
      layout,
      magicFilter,
      this,
      userAccess = userAccess,
    )
      .add(
        lc, "username"
      )
      .add(
        UIAgGridColumnDef.createCol(
          "deactivated",
          headerName = "user.deactivated",
          width = 80,
          valueIconMap = mapOf(true to UIIconType.USER_LOCK, false to null)
        )
      )
      .add("lastLoginTimeAgo", headerName = "login.lastLogin")
      .add(lc, "lastname", "firstname", "personalPhoneIdentifiers", "description")
    if (accessChecker.isLoggedInUserMemberOfAdminGroup) {
      agGrid.add(
        UIAgGridColumnDef.createCol(
          lc,
          "assignedGroups",
          formatter = UIAgGridColumnDef.Formatter.SHOW_LIST_OF_DISPLAYNAMES,
          wrapText = true
        )
      )
      agGrid.add(UIAgGridColumnDef.createCol(lc, "rightsAsString", lcField = "rights", wrapText = true))
      if (useLdapStuff) {
        agGrid.add(UIAgGridColumnDef.createCol(lc, "ldapValues", wrapText = true))
      }
    }
  }

  override fun addMagicFilterElements(elements: MutableList<UILabelledElement>) {
    if (useLdapStuff) {
      elements.add(
        UIFilterListElement("sync", label = translate("user.filter.syncStatus"), defaultFilter = true, multi = false)
          .buildValues(UserPagesFilter.UserSyncFilter.SYNC::class.java)
      )
    }
    elements.add(
      UIFilterListElement("type", label = translate("user.filter.type"), defaultFilter = true, multi = false)
        .buildValues(UserPagesFilter.UserTypeFilter.TYPE::class.java)
    )
    elements.add(
      UIFilterListElement("hrPlanning", label = translate("user.filter.hrPlanning"), defaultFilter = true, multi = false)
        .buildValues(UserPagesFilter.UserHRPlanningFilter.PLANNING_TYPE::class.java)
    )
  }

  override fun preProcessMagicFilter(target: QueryFilter, source: MagicFilter): List<CustomResultFilter<PFUserDO>> {
    val filters = mutableListOf<CustomResultFilter<PFUserDO>>()
    source.entries.find { it.field == "sync" }?.let { filter ->
      filter.synthetic = true
      val values = filter.value.values
      if (!values.isNullOrEmpty() && values.size == 1) {
        val value = values[0]
        try {
          UserPagesFilter.UserSyncFilter.SYNC.valueOf(value).let {
            filters.add(UserPagesFilter.UserSyncFilter(it))
          }
        } catch (ex: IllegalArgumentException) {
          log.warn { "Oups, can't convert '$value': ${ex.message}" }
        }
      }
    }
    source.entries.find { it.field == "type" }?.let { filter ->
      filter.synthetic = true
      val values = filter.value.values
      if (!values.isNullOrEmpty() && values.size == 1) {
        val value = values[0]
        try {
          UserPagesFilter.UserTypeFilter.TYPE.valueOf(value).let {
            filters.add(UserPagesFilter.UserTypeFilter(it))
          }
        } catch (ex: IllegalArgumentException) {
          log.warn { "Oups, can't convert '$value': ${ex.message}" }
        }
      }
    }
    source.entries.find { it.field == "hrPlanning" }?.let { filter ->
      filter.synthetic = true
      val values = filter.value.values
      if (!values.isNullOrEmpty() && values.size == 1) {
        val value = values[0]
        try {
          UserPagesFilter.UserHRPlanningFilter.PLANNING_TYPE.valueOf(value).let {
            filters.add(UserPagesFilter.UserHRPlanningFilter(it))
          }
        } catch (ex: IllegalArgumentException) {
          log.warn { "Oups, can't convert '$value': ${ex.message}" }
        }
      }
    }
    return filters
  }

  @PostMapping("createUid")
  fun createGid(@Valid @RequestBody postData: PostData<User>):
      ResponseAction {
    val data = postData.data
    //data.gidNumber = ldapPosixGroupsUtils.nextFreeGidNumber
    return ResponseAction(targetType = TargetType.UPDATE)
      .addVariable("data", data)
  }

  @PostMapping("createSambaSID")
  fun createSambaSID(@Valid @RequestBody postData: PostData<User>):
      ResponseAction {
    val data = postData.data
    //data.gidNumber = ldapPosixGroupsUtils.nextFreeGidNumber
    return ResponseAction(targetType = TargetType.UPDATE)
      .addVariable("data", data)
  }

  override fun validate(validationErrors: MutableList<ValidationError>, dto: User) {
    super.validate(validationErrors, dto)
    /*dto.gidNumber?.let { gidNumber ->
      if (dto.gidNumber != null && !ldapPosixGroupsUtils.isGivenNumberFree(dto.id ?: -1, gidNumber)) {
        validationErrors.add(
          ValidationError(
            translateMsg("ldap.gidNumber.alreadyInUse", ldapPosixGroupsUtils.nextFreeGidNumber),
            fieldId = "gidNumber",
          )
        )
      }
    }*/
  }

  /**
   * LAYOUT Edit page
   */
  override fun createEditLayout(dto: User, userAccess: UILayout.UserAccess): UILayout {
    val layout = super.createEditLayout(dto, userAccess)
      .add(
        UIRow()
          .add(
            UICol()
              .add(
                lc, "username", "firstname", "lastname", "organization", "email",
                /*"authenticationToken",*/
                "jiraUsername", "hrPlanning", "deactivated"/*, "password"*/
              )
          )
          .add(createUserSettingsCol(UILength(1)))
          .add(UICol().add(lc, "sshPublicKey"))
      )
      /*.add(UISelect<Int>("readonlyAccessUsers", lc,
              multi = true,
              label = "user.assignedGroups",
              additionalLabel = "access.groups",
              autoCompletion = AutoCompletion<Int>(url = "group/aco"),
              labelProperty = "name",
              valueProperty = "id"))
      .add(UISelect<Int>("readonlyAccessUsers", lc,
              multi = true,
              label = "multitenancy.assignedTenants",
              additionalLabel = "access.groups",
              autoCompletion = AutoCompletion<Int>(url = "group/aco"),
              labelProperty = "name",
              valueProperty = "id"))*/
      .add(lc, "description")

    return LayoutUtils.processEditPage(layout, dto, this)
  }

  override val autoCompleteSearchFields = arrayOf("username", "firstname", "lastname", "email")

  override fun queryAutocompleteObjects(request: HttpServletRequest, filter: BaseSearchFilter): List<PFUserDO> {
    val list = super.queryAutocompleteObjects(request, filter)
    if (filter.searchString.isNullOrBlank() || request.getParameter(AutoCompletion.SHOW_ALL_PARAM) != "true") {
      // Show deactivated users only if search string is given or param SHOW_ALL_PARAM is true:
      return list.filter { !it.deactivated } // Remove deactivated users when returning all.
    }
    return list
  }

  companion object {
    internal fun createUserSettingsCol(uiLength: UILength): UICol {
      val userLC = LayoutContext(PFUserDO::class.java)

      val locales = UserLocale.LOCALIZATIONS.map { UISelectValue(Locale(it), translate("locale.$it")) }.toMutableList()
      locales.add(0, UISelectValue(Locale("DEFAULT"), translate("user.defaultLocale")))

      val today = LocalDate.now()
      val formats = Configuration.instance.dateFormats
      val dateFormats = formats.map { createUISelectValue(it, today) }.toMutableList()
      val excelDateFormats = formats.map { createUISelectValue(it, today, true) }.toMutableList()

      val timeNotations = listOf(
        UISelectValue(TimeNotation.H12, translate("timeNotation.12")),
        UISelectValue(TimeNotation.H24, translate("timeNotation.24"))
      )

      return UICol(uiLength).add(UIReadOnlyField("lastLogin", userLC))
        .add(userLC, "timeZone", "personalPhoneIdentifiers")
        .add(UISelect("locale", userLC, required = true, values = locales))
        .add(UISelect("dateFormat", userLC, required = false, values = dateFormats))
        .add(UISelect("excelDateFormat", userLC, required = false, values = excelDateFormats))
        .add(UISelect("timeNotation", userLC, required = false, values = timeNotations))
    }

    private fun createUISelectValue(
      pattern: String,
      today: LocalDate,
      excelDateFormat: Boolean = false
    ): UISelectValue<String> {
      val str = if (excelDateFormat) {
        pattern.replace('y', 'Y').replace('d', 'D')
      } else {
        pattern
      }
      return UISelectValue(str, "$str: ${java.time.format.DateTimeFormatter.ofPattern(pattern).format(today)}")
    }
  }

  private val useLdapStuff: Boolean
    get() = SystemStatus.isDevelopmentMode() || (accessChecker.isLoggedInUserMemberOfAdminGroup && Login.getInstance()
      .hasExternalUsermanagementSystem() && ldapUserDao.isPosixAccountsConfigured)
}
