package org.projectforge.business.poll.filter

import org.projectforge.common.i18n.I18nEnum

enum class PollState(val key: String) : I18nEnum {
    RUNNING("running"), FINISHED("finished");

    override val i18nKey: String
        get() = "poll.$key"
}