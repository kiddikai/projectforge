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

import de.micromata.merlin.word.templating.VariableType
import org.projectforge.framework.i18n.translate
import org.projectforge.framework.time.PFDay
import org.projectforge.framework.utils.NumberHelper
import org.projectforge.menu.MenuItem
import org.projectforge.menu.MenuItemTargetType
import org.projectforge.plugins.merlin.*
import org.projectforge.rest.config.Rest
import org.projectforge.rest.config.RestUtils
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.dto.PostData
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Month
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

@RestController
@RequestMapping("${Rest.URL}/merlinexecution")
class MerlinExecutionPageRest : AbstractDynamicPageRest() {

  @Autowired
  private lateinit var merlinTemplateDao: MerlinTemplateDao

  @Autowired
  private lateinit var merlinRunner: MerlinRunner

  @Autowired
  private lateinit var merlinPagesRest: MerlinPagesRest

  /**
   * Will be called, if the user wants to change his/her observeStatus.
   */
  @PostMapping("execute")
  fun execute(@Valid @RequestBody postData: PostData<MerlinExecutionData>): ResponseEntity<*> {
    val executionData = postData.data
    return RestUtils.downloadFile("hurzel.txt", "Test")
  }

  @GetMapping("dynamic")
  fun getForm(request: HttpServletRequest, @RequestParam("id") idString: String?): FormLayoutData {
    val id = NumberHelper.parseInteger(idString) ?: throw IllegalAccessException("Parameter id not an int.")
    val stats = merlinRunner.getStatistics(id)
    val dbObj = merlinTemplateDao.getById(id)
    val template = merlinPagesRest.transformFromDB(dbObj)
    val col1 = UICol(md = 6)
    val col2 = UICol(md = 6)
    val inputVariables = stats.variables.filter { it.input }
    val size = inputVariables.size
    var counter = 0
    // Place input variables in two columns
    inputVariables.forEach {
      if (counter < size) {
        col1.add(createInputElement(it))
      } else {
        col2.add(createInputElement(it))
      }
      counter += 2
    }
    val layout = UILayout("plugins.merlin.templateExecutor.heading")
      .add(
        UIFieldset(title = "'${template.name}")
          .add(
            UIRow()
              .add(col1)
              .add(col2)
          )
      )
    layout.add(
      UIButton(
        "back",
        translate("back"),
        responseAction = ResponseAction(
          PagesResolver.getListPageUrl(
            MerlinPagesRest::class.java,
            absolute = true
          ), targetType = TargetType.REDIRECT
        ),
      )
    ).add(
      UIButton(
        "execute",
        translate("plugins.merlin.templateExecutor.execute"),
        UIColor.SUCCESS,
        responseAction = ResponseAction(
          RestResolver.getRestUrl(
            this::class.java,
            subPath = "execute"
          ), targetType = TargetType.POST_AND_DOWNLOAD
        ),
        default = true
      )
    )

    if (hasEditAccess(dbObj)) {
      layout.add(
        MenuItem(
          "EDIT",
          i18nKey = "plugins.merlin.title.edit",
          url = PagesResolver.getEditPageUrl(MerlinPagesRest::class.java, template.id),
          type = MenuItemTargetType.REDIRECT
        )
      )
    }
    LayoutUtils.process(layout)
    layout.postProcessPageMenu()

    val dto = MerlinExecutionData(template.id!!, template.name ?: "???")
    dto.inputVariables = mutableMapOf(
      "Vertragsart" to "Arbeitsvertrag",
      "Vertragsnummer" to 1234,
      "Mitarbeiter" to "Kai Reinhard",
      "Geschlecht" to "männlich",
      "MA_Strasse" to "Marie-Calm-Str. 1-5",
      "MA_PLZ" to "34131",
      "MA_Ort" to "Kassel",
      "Vertragsbeginn" to PFDay.of(2020, Month.APRIL, 1),
      "Wochenstunden" to "40",
      "Urlaubstage" to 30,
      "Position" to "Softwareentwickler",
      "Aufgaben" to "Die Welt retten",
      "Gehalt_Zahl" to "40000",
      "Gehalt_Wort" to "vierzigtausend",
    )
    return FormLayoutData(dto, layout, createServerData(request))
  }

  private fun createInputElement(variable: MerlinVariable): UIElement {
    val definition = variable.definition!!
    val dataType = when (definition.type) {
      VariableType.DATE -> UIDataType.DATE
      VariableType.FLOAT -> UIDataType.DECIMAL
      VariableType.INT -> UIDataType.INT
      else -> UIDataType.STRING
    }
    val allowedValuesList = definition.allowedValuesList
    val name = variable.name
    if (allowedValuesList.isNullOrEmpty()) {
      return UIInput("inputVariables.$name", label = "'$name", dataType = dataType, required = definition.isRequired)
    }
    val values = allowedValuesList.map { UISelectValue(it, "$it") }
    return UISelect("inputVariables.$name", label = "'$name", required = definition.isRequired, values = values)
  }

  /**
   * @return true, if the area isn't a personal box and the user has write access.
   */
  private fun hasEditAccess(dbObj: MerlinTemplateDO): Boolean {
    return merlinTemplateDao.hasLoggedInUserUpdateAccess(dbObj, dbObj, false)
  }
}
