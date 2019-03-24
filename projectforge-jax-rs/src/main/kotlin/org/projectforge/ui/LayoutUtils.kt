package org.projectforge.ui

import org.projectforge.framework.i18n.I18nHelper
import org.projectforge.framework.persistence.api.HibernateUtils
import org.projectforge.framework.persistence.entities.DefaultBaseDO

fun translate(i18nKey: String?): String {
    if (i18nKey == null) return "???"
    return I18nHelper.getLocalizedMessage(i18nKey)
}

fun translate(i18nKey: String?, vararg params: Any): String {
    if (i18nKey == null) return "???"
    return I18nHelper.getLocalizedMessage(i18nKey, params)
}

/**
 * Utils for the Layout classes for handling auto max-length (get from JPA entities) and translations as well as
 * generic default layouts for list and edit pages.
 */
class LayoutUtils {

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(LayoutUtils::class.java)

        /**
         * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
         * i18n-keys (by referring the [org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn] annotations of clazz).
         * @return List of all elements used in the layout.
         */
        fun process(layout: UILayout): List<Any?> {
            val elements = processAllElements(layout.getAllElements())
            var counter = 0
            layout.namedContainers.forEach {
                it.key = "nc-${++counter}"
            }
            return elements
        }

        /**
         * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
         * i18n-keys (by referring the [org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn] annotations of clazz).
         * <br>
         * If no named container called "filter-options" is found, it will be attached automatically by calling [addListFilterContainer]
         */
        fun processListPage(layout: UILayout): UILayout {
            var found = false
            layout.namedContainers.forEach {
                if (it.id == "filter-options") {
                    found = true // Container found. Don't attach it automatically.
                }
            }
            if (!found) {
                addListFilterContainer(layout)
            }
            layout
                    .addAction(UIButton("reset", style = UIButtonStyle.DANGER))
                    .addAction(UIButton("search", style = UIButtonStyle.PRIMARY))
            process(layout)
            return layout
        }

        fun addListFilterContainer(layout: UILayout, vararg elements: Any, filterClass: Class<*>? = null, autoAppendDefaultSettings: Boolean? = true) {
            val filterGroup = UIGroup()
            elements.forEach {
                when (it) {
                    is UIElement -> {
                        filterGroup.add(it)
                        if (filterClass != null && it is UILabelledElement) {
                            val property = getId(it)
                            if (property != null) {
                                val elementInfo = ElementsRegistry.getElementInfo(filterClass, property)
                                it.label = elementInfo?.i18nKey
                            }
                        }
                    }
                    is String -> {
                        val element = LayoutUtils.buildLabelInputElement(LayoutSettings(filterClass), it)
                        if (element != null)
                            filterGroup.add(element)
                    }
                    else -> log.error("Element of type '${it::class.java}' not supported as child of filterContainer.")
                }
            }
            if (autoAppendDefaultSettings == true)
                addListDefaultOptions(filterGroup)
            layout.add(UINamedContainer("filter-options").add(filterGroup))
        }

        /**
         * Adds the both checkboxes "only deleted" and "search history" to the given group. This is automatically called
         * by processListPage and appended to the first group found in named container "filter-options".
         */
        fun addListDefaultOptions(group: UIGroup) {
            group
                    .add(UICheckbox("onlyDeleted", label = "onlyDeleted", tooltip = "onlyDeleted.tooltip"))
                    .add(UICheckbox("searchHistory", label = "search.searchHistory", tooltip = "search.searchHistory.additional.tooltip"))
        }

        /**
         * Auto-detects max-length of input fields (by referring the @Column annotations of clazz) and
         * i18n-keys (by referring the @PropertColumn annotations of clazz).<br>
         * Adds the action buttons (cancel, undelete, markAsDeleted, update and/or add dependent on the given data.<br>
         * Calls also fun [process].
         * @see LayoutUtils.process
         */
        fun processEditPage(layout: UILayout, data: DefaultBaseDO?): UILayout {
            layout.addAction(UIButton("cancel", style = UIButtonStyle.DANGER))
            if (data != null && data.id != null) {
                if (data.isDeleted)
                    layout.addAction(UIButton("undelete", style = UIButtonStyle.WARNING))
                else
                    layout.addAction(UIButton("markAsDeleted", style = UIButtonStyle.WARNING))
            }
            //if (restService.prepareClone(restService.newBaseDO())) {
            //    layout.addAction(UIButton("clone", style = UIButtonStyle.PRIMARY))
            //}
            if (data != null && data.id != null) {
                if (!data.isDeleted)
                    layout.addAction(UIButton("update", style = UIButtonStyle.PRIMARY))
            } else {
                layout.addAction(UIButton("create", style = UIButtonStyle.PRIMARY))
            }
            process(layout)
            return layout
        }

        /**
         * @param layoutSettings If [layoutSettings.useInLineLabels] is true, one element is returned including
         * the label (e. g. UIInput). If don't use inline labels, a group containing a label and an input field is returned.
         */
        internal fun buildLabelInputElement(layoutSettings: LayoutSettings, id: String): UIElement? {
            val element = ElementsRegistry.buildElement(layoutSettings, id)
            if (layoutSettings.useInlineLabels) {
                return element
            }
            val group = UIGroup()
            val label = UILabel()
            val elementInfo = ElementsRegistry.getElementInfo(layoutSettings, id)
            setLabels(elementInfo, label)
            group.add(label, element)
            return group
        }

        internal fun setLabels(elementInfo: ElementsRegistry.ElementInfo?, element: UILabelledElement) {
            if (elementInfo == null)
                return
            if (!elementInfo.i18nKey.isNullOrEmpty())
                element.label = elementInfo.i18nKey
            if (!elementInfo.additionalI18nKey.isNullOrEmpty())
                element.additionalLabel = elementInfo.additionalI18nKey
        }

        /**
         * Does translation of buttons and UILabels
         * @param elements List of all elements used in the layout.
         * @param clazz The class of the property to search for annotations [@PropertyInfo]
         * @return The unmodified parameter elements.
         * @see HibernateUtils.getPropertyLength
         */
        private fun processAllElements(elements: List<Any>): List<Any?> {
            var counter = 0
            elements.forEach {
                if (it is UIElement) it.key = "el-${++counter}"
                when (it) {
                    is UILabelledElement -> {
                        it.label = getLabelTransformation(it.label, it as UIElement)
                        it.additionalLabel = getLabelTransformation(it.additionalLabel, it, additionalLabel = true)
                        it.tooltip = getLabelTransformation(it.tooltip)
                    }
                    is UITableColumn -> {
                        val translation = getLabelTransformation(it.title)
                        if (translation != null) it.title = translation
                    }
                    is UIButton -> {
                        if (it.title == null) {
                            val i18nKey = when (it.id) {
                                "cancel" -> "cancel"
                                "clone" -> "clone"
                                "create" -> "create"
                                "markAsDeleted" -> "markAsDeleted"
                                "reset" -> "reset"
                                "search" -> "search"
                                "undelete" -> "undelete"
                                "update" -> "save"
                                else -> null
                            }
                            if (i18nKey == null) {
                                log.error("i18nKey not found for action button '${it.id}'.")
                            } else {
                                it.title = translate(i18nKey)
                            }
                        }
                    }
                }
            }
            return elements
        }

        /**
         * @return The id of the given element if supported.
         */
        internal fun getId(element: UIElement?, followLabelReference: Boolean = true): String? {
            if (element == null) return null
            if (followLabelReference == true && element is UILabel) {
                return getId(element.reference)
            }
            return when (element) {
                is UIInput -> element.id
                is UICheckbox -> element.id
                is UISelect -> element.id
                is UIMultiSelect -> element.id
                is UITextarea -> element.id
                is UITableColumn -> element.id
                else -> null
            }
        }

        /**
         * If the given label starts with "'" the label itself as substring after "'" will be returned: "'This is an text." -> "This is an text"<br>
         * Otherwise method [translate] will be called and the result returned.
         * @param label to process
         * @return Modified label or unmodified label.
         */
        internal fun getLabelTransformation(label: String?, labelledElement: UIElement? = null, additionalLabel: Boolean = false): String? {
            if (label == null) {
                if (labelledElement is UILabelledElement) {
                    val layoutSettings = labelledElement.layoutSettings
                    if (layoutSettings != null) {
                        val id = getId(labelledElement)
                        if (id != null) {
                            var elementInfo = ElementsRegistry.getElementInfo(layoutSettings, id)
                            if (!additionalLabel && elementInfo?.i18nKey != null) {
                                return translate(elementInfo.i18nKey)
                            }
                            if (additionalLabel && elementInfo?.additionalI18nKey != null) {
                                return translate(elementInfo.additionalI18nKey)
                            }
                        }
                    }
                }
                return null
            }
            if (label.startsWith("'") == true)
                return label.substring(1)
            return translate(label)
        }

    }
}