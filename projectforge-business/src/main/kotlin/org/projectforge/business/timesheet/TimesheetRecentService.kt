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

package org.projectforge.business.timesheet

import mu.KotlinLogging
import org.projectforge.business.user.service.UserPrefService
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.PFDateTime
import org.projectforge.framework.utils.RecentQueue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

@Service
class TimesheetRecentService {
    @Autowired
    private lateinit var userPrefService: UserPrefService

    @Autowired
    private lateinit var timesheetDao: TimesheetDao

    fun getRecentTimesheets(): List<TimesheetRecentEntry> {
        return getRecentTimesheetsQueue(ThreadLocalUserContext.getUserId()).recentList
    }

    fun getRecentTimesheet(): TimesheetRecentEntry? {
        return getRecentTimesheetsQueue(ThreadLocalUserContext.getUserId()).recent
    }

    fun addRecentTimesheet(entry: TimesheetRecentEntry) {
        if (entry.description.isNullOrBlank() && entry.location.isNullOrBlank() && entry.taskId == null) {
            // Don't append empty entries.
            return
        }
        getRecentTimesheetsQueue(ThreadLocalUserContext.getUserId()).append(entry)
    }

    fun getRecentLocations(): List<String> {
        return getRecentLocationsQueue(ThreadLocalUserContext.getUserId()).recentList
    }

    fun addRecentLocation(location: String?) {
        if (location.isNullOrBlank()) {
            return
        }
        getRecentLocationsQueue(ThreadLocalUserContext.getUserId()).append(location)
    }

    fun getRecentTaskIds(): List<Int> {
        return getRecentTaskIdsQueue(ThreadLocalUserContext.getUserId()).recentList
    }

    fun addRecentTaskId(taskId: Int?) {
        taskId ?: return
        getRecentTaskIdsQueue(ThreadLocalUserContext.getUserId()).append(taskId)
    }

    private fun getRecentTimesheetsQueue(userId: Int): RecentQueue<TimesheetRecentEntry> {
        return getRecentQueue(userId, "recent.timesheets") { recentQueue -> readRecentTimesheets(recentQueue, userId) }
    }

    private fun getRecentLocationsQueue(userId: Int): RecentQueue<String> {
        return getRecentQueue(userId, "recent.locations") { recentQueue -> readRecentLocations(recentQueue, userId) }
    }

    private fun getRecentTaskIdsQueue(userId: Int): RecentQueue<Int> {
        return getRecentQueue(userId, "recent.taskIds") { recentQueue -> readRecentTaskIds(recentQueue, userId) }
    }

    private fun <T> getRecentQueue(userId: Int, id: String, read: (recentQueue: RecentQueue<T>) -> Unit): RecentQueue<T> {
        var recentQueue: RecentQueue<T>? = null
        try {
            @Suppress("UNCHECKED_CAST")
            recentQueue = userPrefService.getEntry(PREF_AREA, id, RecentQueue::class.java, userId) as? RecentQueue<T>
        } catch (ex: Exception) {
            log.error("Unexpected exception while getting recent $id for user #$userId: ${ex.message}.", ex)
        }
        if (recentQueue == null) {
            recentQueue = RecentQueue()
            // Put as volatile entry (will be created after restart oder re-login.
            userPrefService.putEntry(PREF_AREA, id, recentQueue, false, userId)
            read(recentQueue)
        }
        return recentQueue
    }


    private fun readRecentTimesheets(recentQueue: RecentQueue<TimesheetRecentEntry>, userId: Int) {
        log.info { "Getting recent timesheets for user #$userId." }
        val added = mutableSetOf<TimesheetRecentEntry>()
        val list = timesheetDao.getList(getTimesheetFilter(userId)) ?: return
        for (timesheet in list) {
            val entry = TimesheetRecentEntry(
                    taskId = timesheet.taskId,
                    userId = timesheet.userId,
                    kost2Id = timesheet.kost2Id,
                    location = timesheet.location,
                    description = timesheet.description)
            if (!added.contains(entry)) {
                added.add(entry)
                recentQueue.addOnly(entry)
                if (added.size >= MAX_RECENT) {
                    break
                }
            }
        }
        log.info { "Found ${recentQueue.size()}/$MAX_RECENT distinct entries in ${list.size} timesheets of user #$userId since ${sinceDate.isoString}." }
    }

    private fun readRecentLocations(recentQueue: RecentQueue<String>, userId: Int) {
        log.info { "Getting recent timesheet locations for user #$userId." }
        val added = mutableSetOf<String>()
        val list = timesheetDao.getRecentLocation(sinceDate.utilDate) ?: return
        for (location in list) {
            if (!added.contains(location)) {
                added.add(location)
                recentQueue.addOnly(location)
                if (added.size >= MAX_RECENT) {
                    break
                }
            }
        }
        log.info { "Found ${recentQueue.size()}/$MAX_RECENT distinct locations of ${list.size} timesheet locations of user #$userId since ${sinceDate.isoString}." }
    }

    private fun readRecentTaskIds(recentQueue: RecentQueue<Int>, userId: Int) {
        log.info { "Getting recent timesheet task id's for user #$userId." }
        val added = mutableSetOf<Int>()
        val list = timesheetDao.getList(getTimesheetFilter(userId)) ?: return
        for (timesheet in list) {
            val taskId = timesheet.taskId ?: continue
            if (!added.contains(taskId)) {
                added.add(taskId)
                recentQueue.addOnly(taskId)
                if (added.size >= MAX_RECENT) {
                    break
                }
            }
        }
        log.info { "Found ${recentQueue.size()}/$MAX_RECENT distinct task id's of ${list.size} timesheets of user #$userId since ${sinceDate.isoString}." }
    }

    private val sinceDate
        get() = PFDateTime.now().minusYears(YEARS_AGO).beginOfDay

    private fun getTimesheetFilter(userId: Int): TimesheetFilter {
        val filter = TimesheetFilter()
        filter.userId = userId
        filter.startTime = sinceDate.utilDate
        return filter
    }

    companion object {
        private const val PREF_AREA = "timesheet"
        private const val MAX_RECENT = 50
        private const val YEARS_AGO = 1L
    }
}
