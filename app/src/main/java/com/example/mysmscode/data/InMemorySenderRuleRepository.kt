package com.example.mysmscode.data

import com.example.mysmscode.domain.SenderRule

class InMemorySenderRuleRepository {

    private val rulesById = linkedMapOf<Long, SenderRule>()
    private var nextId = 1L

    fun save(rule: SenderRule): RepositorySaveResult<SenderRule> {
        if (rulesById.values.any { it.senderNumber == rule.senderNumber }) {
            return RepositorySaveResult.DuplicateSenderNumber
        }

        val storedRule = rule.copy(id = if (rule.id == 0L) nextId++ else rule.id)
        rulesById[storedRule.id] = storedRule
        return RepositorySaveResult.Success(storedRule)
    }

    fun update(rule: SenderRule): RepositorySaveResult<SenderRule> {
        if (!rulesById.containsKey(rule.id)) {
            return RepositorySaveResult.NotFound
        }
        if (rulesById.values.any { it.id != rule.id && it.senderNumber == rule.senderNumber }) {
            return RepositorySaveResult.DuplicateSenderNumber
        }
        rulesById[rule.id] = rule
        return RepositorySaveResult.Success(rule)
    }

    fun deleteById(id: Long) {
        rulesById.remove(id)
    }

    fun findById(id: Long): SenderRule? = rulesById[id]

    fun findBySenderNumber(senderNumber: String): SenderRule? = rulesById.values.firstOrNull { it.senderNumber == senderNumber }
}
