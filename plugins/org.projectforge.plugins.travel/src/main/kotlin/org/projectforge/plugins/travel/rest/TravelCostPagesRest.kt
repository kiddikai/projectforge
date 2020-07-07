/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.plugins.travel.rest

import org.projectforge.business.fibu.kost.Kost2ArtDO
import org.projectforge.business.fibu.kost.Kost2DO
import org.projectforge.framework.jcr.AttachmentsService
import org.projectforge.model.rest.RestPaths
import org.projectforge.plugins.travel.TravelCostDO
import org.projectforge.plugins.travel.TravelCostDao
import org.projectforge.plugins.travel.dto.TravelCost
import org.projectforge.rest.config.JacksonConfiguration
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTOPagesRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.rest.dto.PostData
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

// TODO: Add jcr support (see ContractPagesRest/jcr and attachment*)
/**
 * @author Jan Brümmer (j.bruemmer@micromata.de)
 */
@RestController
@RequestMapping("${Rest.URL}/travelCost")
class TravelCostPagesRest : AbstractDTOPagesRest<TravelCostDO, TravelCost, TravelCostDao>(TravelCostDao::class.java, "plugins.travel.entry.title") {

    @Autowired
    private lateinit var attachmentsService: AttachmentsService

    @Autowired
    private lateinit var travelCostDao: TravelCostDao

    override fun transformFromDB(obj: TravelCostDO, editMode: Boolean): TravelCost {
        travelCostDao.deserizalizeValueObject(obj)
        val travelCost = TravelCost()
        travelCost.copyFrom(obj)
        return travelCost
    }

    override fun transformForDB(dto: TravelCost): TravelCostDO {
        val travelCostDO = TravelCostDO()
        dto.copyTo(travelCostDO)
        if(travelCostDO.kost2 == null){
            travelCostDO.kost2 = Kost2DO()
            travelCostDO.kost2!!.kost2Art = Kost2ArtDO().withId(dto.endziffer)
        }

        travelCostDO.kost2!!.nummernkreis = dto.nummernkreis!!
        travelCostDO.kost2!!.bereich = dto.bereich!!
        travelCostDO.kost2!!.teilbereich = dto.teilbereich!!

        if(travelCostDO.kost2?.kost2Art != null){
            travelCostDO.kost2?.kost2Art?.id = dto.endziffer
        }

        travelCostDO.cateringValueObject = dto.catering

        return travelCostDO
    }

    @PostConstruct
    private fun postConstruct() {
        /**
         * Enable attachments for this entity.
         */
        enableJcr()
        JacksonConfiguration.registerAllowedUnknownProperties(TravelCost::class.java, "statusAsString")
    }

    /**
     * Initializes new TravelCosts for adding.
     */
    override fun newBaseDO(request: HttpServletRequest?): TravelCostDO {
        val travelCost = super.newBaseDO(request)
        //travelCost.user = ThreadLocalUserContext.getUser()
        return travelCost
    }

    override fun onWatchFieldsUpdate(request: HttpServletRequest, dto: TravelCost, watchFieldsTriggered: Array<String>?): ResponseEntity<ResponseAction> {
        val startDate = dto.beginOfTravel
        val endDate = dto.endOfTravel
        if (watchFieldsTriggered?.contains("beginOfTravel") == true && startDate != null) {
            if (endDate == null || endDate.before(startDate)) {
                dto.endOfTravel = startDate
            }
        } else if (watchFieldsTriggered?.contains("endOfTravel") == true && endDate != null) {
            if (startDate == null || endDate.before(startDate)) {
                dto.beginOfTravel = endDate
            }
        }
        return ResponseEntity.ok(ResponseAction(targetType = TargetType.UPDATE).addVariable("data", dto))
    }

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.createUIResultSetTable()
                        .add(UITableColumn("employee.user.displayName", "plugins.travel.entry.user"))
                        .add(lc, "employee.staffNumber", "beginOfTravel", "endOfTravel", "destination", "kilometers")
                        .add(UITableColumn("formattedRefundByKilometer",
                                "TODO"))
                        .add(UITableColumn("formattedRefundByKilometerPassenger",
                                "TODO"))
                        .add(UITableColumn("totalRefund",
                                "TODO"))
                        .add(UITableColumn("cateringPrice",
                                "TODO"))
                        .add(UITableColumn("cateringNumber",
                                "TODO"))
                        .add(UITableColumn("cateringCost",
                                "TODO")))
        return LayoutUtils.processListPage(layout, this)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dto: TravelCost, userAccess: UILayout.UserAccess): UILayout {
        val costNumber = UICustomized("cost.number")
            costNumber.add("nummernkreis", dto.nummernkreis!!)
                    .add("bereich", dto.bereich!!)
                    .add("teilbereich", dto.teilbereich!!)
                    .add("endziffer", dto.endziffer!!)

        //val location = UIInput("location", lc).enableAutoCompletion(this)
        val layout = super.createEditLayout(dto, userAccess)
                .add(UISelect.createEmployeeSelect(lc, "employee", false, "plugins.travel.entry.user"))
                .add(lc, "reasonOfTravel", "destination")
                .add(costNumber)
                .add(lc, "beginOfTravel", "endOfTravel")
                .add(lc, "startLocation", "returnLocation")
                .add(UICustomized("catering.day"))
                .add(lc, "kilometers")
                .add(UICheckbox("hotel", lc))
                .add(UICheckbox("rentalCar", lc))
                .add(UICheckbox("train", lc))
                .add(UICheckbox("flight", lc))
                .add(lc, "assumptionOfCosts")
                .add(UICheckbox("receiptsCompletelyAvailable", lc))

        layout.watchFields.addAll(arrayOf("beginOfTravel", "endOfTravel"))

        //additionalLabel = "access.users",
        return LayoutUtils.processEditPage(layout, dto, this)
    }
}