package com.example.mysmscode.data

import com.example.mysmscode.domain.PhoneNumberNormalizer
import com.example.mysmscode.domain.SenderRule
import com.example.mysmscode.domain.hasLogicalSenderConflictWith

class InMemorySenderRuleRepository(
    private val phoneNumberNormalizer: PhoneNumberNormalizer = PhoneNumberNormalizer(),
) {

    private val rulesById = linkedMapOf<Long, SenderRule>()
    private var nextId = 1L

    fun save(rule: SenderRule): RepositorySaveResult<SenderRule> {
        if (rulesById.values.any { it.senderNumber == rule.senderNumber }) {
            return RepositorySaveResult.DuplicateSenderNumber
        }
        findConflictingRule(rule)?.let { conflictingRule ->
            return RepositorySaveResult.ConflictingSenderRule(conflictingRule.senderNumber)
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
        findConflictingRule(rule)?.let { conflictingRule ->
            return RepositorySaveResult.ConflictingSenderRule(conflictingRule.senderNumber)
        }
        rulesById[rule.id] = rule
        return RepositorySaveResult.Success(rule)
    }

    fun deleteById(id: Long) {
        rulesById.remove(id)
    }

    fun findById(id: Long): SenderRule? = rulesById[id]

    fun findBySenderNumber(senderNumber: String): SenderRule? = rulesById.values.firstOrNull { it.senderNumber == senderNumber }

    private fun findConflictingRule(rule: SenderRule): SenderRule? {
        return rulesById.values.firstOrNull { existingRule ->
            existingRule.id != rule.id &&
                rule.hasLogicalSenderConflictWith(
                    existingRule,
                    phoneNumberNormalizer = phoneNumberNormalizer,
                )
        }
    }
}
