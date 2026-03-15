package com.example.mysmscode.data

import com.example.mysmscode.domain.SenderRule

class InMemorySenderRuleRepository {

    private val rulesBySenderNumber = linkedMapOf<String, SenderRule>()

    fun save(rule: SenderRule): RepositorySaveResult<SenderRule> {
        if (rulesBySenderNumber.containsKey(rule.senderNumber)) {
            return RepositorySaveResult.DuplicateSenderNumber
        }

        rulesBySenderNumber[rule.senderNumber] = rule
        return RepositorySaveResult.Success(rule)
    }

    fun findBySenderNumber(senderNumber: String): SenderRule? = rulesBySenderNumber[senderNumber]
}
