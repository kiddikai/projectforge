package org.projectforge.rest.poll

import org.projectforge.business.poll.PollDO
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.rest.dto.BaseDTO
import org.projectforge.rest.poll.types.Question
import java.time.LocalDate

class Poll(
    var title: String? = null,
    var description: String? = null,
    var owner: PFUserDO? = null,
    var location: String? = null,
    var date: LocalDate? = null,
    var deadline: LocalDate? = null,
    var state: PollDO.State? = PollDO.State.RUNNING,
    var questionType: String? = null,
    var inputFields: MutableList<Question>? = mutableListOf(),
    var canSeeResultUsers: String? = null,
    var canEditPollUsers: String? = null,
    var canVoteInPoll: String? = null
) : BaseDTO<PollDO>() {

}