package org.projectforge.rest.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
open class BirthdayListConfiguration {
    @Value("\${projectforge.birthdaylist.organization}")
    open var organization: String? = null

    @Value("\${projectforge.birthdaylist.emails}")
    open var emails: String? = null
}