package com.hyuchia.orbit.style

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/** The app's own rules, which ktlint finds through `META-INF/services`. */
public class StyleRuleSetProvider : RuleSetProviderV3(RuleSetId("orbit")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(
        RuleProvider { BlankLinesAroundBlocksRule() },
        RuleProvider { BlankLinesAroundDocumentedMembersRule() },
    )
}
