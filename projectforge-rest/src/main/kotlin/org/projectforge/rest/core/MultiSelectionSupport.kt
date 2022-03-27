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

package org.projectforge.rest.core

import org.projectforge.framework.persistence.api.IdObject
import org.projectforge.ui.UIAgGrid
import org.projectforge.ui.UILayout
import java.io.Serializable
import javax.servlet.http.HttpServletRequest

/**
 * Supports multi selection and updates of list pages.
 */
object MultiSelectionSupport {
  @JvmStatic
  fun registerEntitiesForSelection(
    request: HttpServletRequest,
    clazz: Class<out Any>,
    entityCollection: Collection<IdObject<*>>
  ) {
    registerEntitiesForSelection(request, clazz.name, entityCollection)
  }

  @JvmStatic
  fun registerEntitiesForSelection(
    request: HttpServletRequest,
    identifier: String,
    entityCollection: Collection<IdObject<*>>
  ) {
    val idList = entityCollection.map { it.id }
    registerEntityIdsForSelection(request, identifier, idList)
  }

  @JvmStatic
  fun registerEntityIdsForSelection(request: HttpServletRequest, identifier: String, idList: Collection<*>) {
    registerSelectedEntityIds(request, identifier, null) // Clear selection.
    ExpiringSessionAttributes.setAttribute(request, "$SESSSION_ATTRIBUTE_ENTITIES:$identifier", idList, TTL_MINUTES)
  }

  fun getRegisteredEntityIds(request: HttpServletRequest, clazz: Class<out Any>): Collection<Serializable>? {
    return getRegisteredEntityIds(request, clazz.name)
  }

  @Suppress("UNCHECKED_CAST")
  fun getRegisteredEntityIds(request: HttpServletRequest, identifier: String): Collection<Serializable>? {
    return ExpiringSessionAttributes.getAttribute(
      request,
      "$SESSSION_ATTRIBUTE_ENTITIES:$identifier"
    ) as? Collection<Serializable>
  }

  /**
   * Register the selected entities sent by the client for later recovering (e. g. on reload).
   */
  fun registerSelectedEntityIds(request: HttpServletRequest, clazz: Class<out Any>, idList: Collection<Serializable>?) {
    registerSelectedEntityIds(request, clazz.name, idList)
  }

  /**
   * Register the selected entities sent by the client for later recovering (e. g. on reload).
   */
  fun registerSelectedEntityIds(request: HttpServletRequest, identifier: String, idList: Collection<Serializable>?) {
    if (idList == null) {
      ExpiringSessionAttributes.removeAttribute(request, "$SESSSION_ATTRIBUTE_SELECTED_ENTITIES:$identifier")
      return
    }
    ExpiringSessionAttributes.setAttribute(
      request,
      "$SESSSION_ATTRIBUTE_SELECTED_ENTITIES:$identifier",
      idList,
      TTL_MINUTES
    )
  }

  /**
   * Gets the previous selected entities.
   */
  fun getRegisteredSelectedEntityIds(request: HttpServletRequest, clazz: Class<out Any>): Collection<Serializable>? {
    return getRegisteredSelectedEntityIds(request, clazz.name)
  }

  /**
   * Gets the previous selected entities.
   */
  fun getRegisteredSelectedEntityIds(request: HttpServletRequest, identifier: String): Collection<Serializable>? {
    return ExpiringSessionAttributes.getAttribute(
      request,
      "$SESSSION_ATTRIBUTE_SELECTED_ENTITIES:$identifier"
    ) as? Collection<Serializable>
  }


  @JvmStatic
  fun getMultiSelectionParamMap(): MutableMap<String, Any> {
    return mutableMapOf(REQUEST_PARAM_MULTI_SELECTION to true)
  }

  fun isMultiSelection(request: HttpServletRequest): Boolean {
    return request.getParameter(REQUEST_PARAM_MULTI_SELECTION) == "true"
  }

  /**
   * Creates UIGridTable and adds it to the given layout. Will also handle flag layout.hideSearchFilter o
   * multi-selection mode.
   */
  fun prepareUIGrid4ListPage(
    request: HttpServletRequest,
    layout: UILayout,
    pagesRest: Class<out AbstractDynamicPageRest>
  ): UIAgGrid {
    val table = UIAgGrid.createUIResultSetTable()
    if (isMultiSelection(request)) {
      layout.hideSearchFilter = true
      table.urlForMultiSelect = RestResolver.getRestUrl(pagesRest, AbstractMultiSelectedPage.URL_PATH_SELECTED)
    }
    layout.add(table)
    return table
  }

  private const val TTL_MINUTES = 60
  private val SESSSION_ATTRIBUTE_ENTITIES = "{${MultiSelectionSupport::class.java.name}.entities"
  private val SESSSION_ATTRIBUTE_SELECTED_ENTITIES = "{${MultiSelectionSupport::class.java.name}.selected.entities"
  private val REQUEST_PARAM_MULTI_SELECTION = "multiSelectionMode"
}