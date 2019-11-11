/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.persistence.api.impl

import org.hibernate.search.annotations.ClassBridge
import org.hibernate.search.jpa.Search
import org.hibernate.search.query.dsl.BooleanJunction
import org.hibernate.search.query.dsl.QueryBuilder
import org.projectforge.common.ClassUtils
import org.projectforge.common.props.PropUtils
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import org.projectforge.framework.persistence.api.QueryFilter
import org.projectforge.framework.persistence.api.SortProperty
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager

internal class DBQueryBuilderByFullText<O : ExtendedBaseDO<Int>>(
        private val baseDao: BaseDao<O>,
        private val entityManager: EntityManager,
        /**
         * Only for fall back to criteria search if no predicates found for full text search.
         */
        private val queryFilter: QueryFilter,
        val useMultiFieldQueryParser: Boolean = false,
        private var usedSearchFields: Array<String> = getUsedSearchFields(baseDao)) {
    companion object {
        private val log = LoggerFactory.getLogger(DBQueryBuilderByFullText::class.java)

        private val supportedFieldsMap = mutableMapOf<Class<out ExtendedBaseDO<Int>>, Array<String>>()

        fun getUsedSearchFields(baseDao: BaseDao<*>): Array<String> {
            var result: Array<String>? = supportedFieldsMap[baseDao.doClass]
            if (result != null) return result
            val stringSet = mutableSetOf<String>()
            val fields = baseDao.searchFields
            fields.forEach {
                val type = PropUtils.getField(baseDao.doClass, it, true)?.type
                if (type != null) {
                    if (type.isAssignableFrom(String::class.java)
                            || type.isAssignableFrom(Integer::class.java)
                            || type.isAssignableFrom(Int::class.java)
                            || type.isAssignableFrom(java.util.Date::class.java)
                            //|| type.isEnum() // Doesn't work
                            || type.isAssignableFrom(java.sql.Date::class.java)) {
                        stringSet.add(it) // Search only for fields of type string and int, if no special field is specified.
                    } else {
                        if (log.isDebugEnabled) log.debug("Type '${type.name}' of search property '${baseDao.doClass}.$it' not supported.")
                    }
                } else {
                    // Check @ClassBridge annotation:
                    val ann = ClassUtils.getClassAnnotationOfField(baseDao.doClass, it, ClassBridge::class.java)
                    if (ann != null && (ann.name == it || it.endsWith(ann.name))) {
                        stringSet.add(it) // Search for class bridge name.
                    } else {
                        log.warn("Search property '${baseDao.doClass}.$it' not found (ignoring it).")
                    }
                }
            }
            result = stringSet.toTypedArray()
            log.info("${baseDao.doClass.simpleName}: Adding supported search fields to class: ${result.joinToString(", ")}")
            supportedFieldsMap[baseDao.doClass] = result
            return result
        }
    }

    private var queryBuilder: QueryBuilder
    private var boolJunction: BooleanJunction<*>
    private val fullTextEntityManager = Search.getFullTextEntityManager(entityManager)
    private val sortOrders = mutableListOf<SortProperty>()
    private val multiFieldQuery = mutableListOf<String>()

    init {
        queryBuilder = fullTextEntityManager.searchFactory.buildQueryBuilder().forEntity(baseDao.doClass).get()
        boolJunction = queryBuilder.bool()
    }

    fun fieldSupported(field: String): Boolean {
        return usedSearchFields.contains(field)
    }

    /**
     * @return true, if the predicate was added to query builder, otherwise false (must be handled as result predicate instead).
     */
    fun add(predicate: DBPredicate): Boolean {
        if (!predicate.fullTextSupport) return false
        val field = predicate.field
        if (field != null && !usedSearchFields.contains(field)) return false
        predicate.addTo(this)
        return true
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun equal(field: String, value: Any): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [equal] +$field:$value")
                multiFieldQuery.add("+$field:$value")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [equal] boolJunction.must(qb.keyword().onField('$field').matching('$value')...)")
                boolJunction = boolJunction.must(queryBuilder.keyword().onField(field).matching(value).createQuery())
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun notEqual(field: String, value: Any): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [notEqual] -$field:$value")
                multiFieldQuery.add("-$field:$value")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [notEqual] boolJunction.must(qb.keyword().onField('$field').matching('$value')...).not()")
                boolJunction = boolJunction.must(queryBuilder.keyword().onField(field).matching(value).createQuery()).not()
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun <O : Comparable<O>> between(field: String, from: O, to: O): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [between] +$field:[$from TO $to]")
                multiFieldQuery.add("+$field:[$from TO $to]")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [between] boolJunction.must(qb.range().onField('$field').from('$from').to('$to')...)")
                boolJunction = boolJunction.must(queryBuilder.range().onField(field).from(from).to(to).createQuery())
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun <O : Comparable<O>> greater(field: String, from: O): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [greater] +$field:{$from TO *}")
                multiFieldQuery.add("+$field:{$from TO *}")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [greater] boolJunction.must(qb.range().onField('$field').above('$from').excludeLimit()...)")
                boolJunction = boolJunction.must(queryBuilder.range().onField(field).above(from).excludeLimit().createQuery())
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun <O : Comparable<O>> greaterEqual(field: String, from: O): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [greaterEqual] +$field:[$from TO *]")
                multiFieldQuery.add("+$field:[$from TO *]")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [greaterEqual] boolJunction.must(qb.range().onField('$field').above('$from')...)")
                boolJunction = boolJunction.must(queryBuilder.range().onField(field).above(from).createQuery())
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun <O : Comparable<O>> less(field: String, to: O): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [less] +$field:{* TO $to}")
                multiFieldQuery.add("+$field:{* TO $to}")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [less] boolJunction.must(qb.range().below('$field').excludeLimit()...)")
                boolJunction = boolJunction.must(queryBuilder.range().onField(field).below(to).excludeLimit().createQuery())
            }
            return true
        }
        return false
    }

    /**
     * @return true if the given field is indexed, otherwise false (dbMatcher should be used instead).
     */
    fun <O : Comparable<O>> lessEqual(field: String, to: O): Boolean {
        if (usedSearchFields.contains(field)) {
            if (useMultiFieldQueryParser) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [lessEqual] +$field:[* TO $to]")
                multiFieldQuery.add("+$field:[* TO $to]")
            } else {
                if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [lessEqual] boolJunction.must(qb.range().below('$field')...)")
                boolJunction = boolJunction.must(queryBuilder.range().onField(field).below(to).createQuery())
            }
            return true
        }
        return false
    }

    fun ilike(field: String, value: String) {
        search(value, field)
    }

    fun fulltextSearch(searchString: String) {
        search(searchString, *usedSearchFields)
    }

    fun createResultIterator(resultPredicates: List<DBPredicate>): DBResultIterator<O> {
        return when {
            useMultiFieldQueryParser -> {
                DBFullTextResultIterator(baseDao, fullTextEntityManager, resultPredicates, sortOrders.toTypedArray(), usedSearchFields = usedSearchFields, multiFieldQuery = multiFieldQuery)
            }
            boolJunction.isEmpty -> { // Shouldn't occur:
                // No restrictions found, so use normal criteria search without where clause.
                DBQueryBuilderByCriteria(baseDao, entityManager, queryFilter).createResultIterator(resultPredicates)
            }
            else -> {
                DBFullTextResultIterator(baseDao, fullTextEntityManager, resultPredicates, sortOrders.toTypedArray(), fullTextQuery = boolJunction.createQuery())
            }
        }
    }

    private fun search(value: String, vararg fields: String) {
        if (value.isBlank()) {
            return
        }
        if (useMultiFieldQueryParser) {
            if (fields.isNotEmpty() && fields.size == 1) {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [search] ${fields[0]}:$value")
                multiFieldQuery.add("${fields[0]}:$value")
            } else {
                if (log.isDebugEnabled) log.debug("Adding multifieldQuery (${baseDao.doClass.simpleName}): [search] $value")
                multiFieldQuery.add(value)
            }
        } else {
            val context = if (value.indexOf('*') >= 0) {
                if (fields.size > 1) {
                    if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [search] boolJunction.must(qb.keyword().wildcard().onFields(*).matching($value)...): fields:${fields.joinToString(", ")}")
                    queryBuilder.keyword().wildcard().onFields(*fields)
                } else {
                    if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [search] boolJunction.must(qb.keyword().wildcard().onField('${fields[0]}').matching($value)...)")
                    queryBuilder.keyword().wildcard().onField(fields[0])
                }
            } else {
                if (fields.size > 1) {
                    if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [search] boolJunction.must(qb.keyword().onFields(*).matching($value)...): fields:${fields.joinToString(", ")}")
                    queryBuilder.keyword().onFields(*fields)
                } else {
                    if (log.isDebugEnabled) log.debug("Adding fulltext search (${baseDao.doClass.simpleName}): [search] boolJunction.must(qb.keyword().onField('${fields[0]}').matching($value)...)")
                    queryBuilder.keyword().onField(fields[0])
                }
            }
            boolJunction = boolJunction.must(context.ignoreAnalyzer().matching(value).createQuery())
        }
    }

    fun addOrder(sortProperty: SortProperty) {
        sortOrders.add(sortProperty)
    }
}
