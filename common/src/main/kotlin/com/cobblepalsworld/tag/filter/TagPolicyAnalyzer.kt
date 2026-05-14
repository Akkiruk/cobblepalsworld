package com.cobblepalsworld.tag.filter

import com.cobblepalsworld.tag.TagSpec
import com.cobblepalsworld.tag.TagType
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries

enum class TagPolicySeverity {
    INFO,
    WARNING,
    BLOCKING
}

data class TagPolicyLine(
    val moduleIndex: Int,
    val tagType: TagType,
    val spec: TagSpec
)

data class TagPolicyIssue(
    val severity: TagPolicySeverity,
    val label: String,
    val detail: String,
    val moduleIndexes: Set<Int>
)

object TagPolicyAnalyzer {
    fun issuesByModule(lines: List<TagPolicyLine>): Map<Int, List<TagPolicyIssue>> {
        val issues = mutableMapOf<Int, MutableList<TagPolicyIssue>>()

        lines.forEach { line ->
            lineIssues(line).forEach { issue ->
                issues.getOrPut(line.moduleIndex) { mutableListOf() } += issue
            }
        }

        sharedIssues(lines).forEach { issue ->
            issue.moduleIndexes.forEach { moduleIndex ->
                issues.getOrPut(moduleIndex) { mutableListOf() } += issue
            }
        }

        return issues.mapValues { (_, moduleIssues) ->
            moduleIssues.sortedWith(compareBy<TagPolicyIssue> { severityRank(it.severity) }.thenBy { it.label })
        }
    }

    fun commandPostIssues(lines: List<TagPolicyLine>): List<TagPolicyIssue> {
        return lines.flatMap(::lineIssues) + sharedIssues(lines)
    }

    private fun lineIssues(line: TagPolicyLine): List<TagPolicyIssue> {
        val issues = mutableListOf<TagPolicyIssue>()
        val spec = line.spec
        val filter = spec.filter

        if (line.tagType.supportsBinding && spec.boundPos == null && spec.boundArea == null) {
            issues += TagPolicyIssue(
                severity = TagPolicySeverity.BLOCKING,
                label = "Needs target",
                detail = "Bind this role card before assigning crew to it.",
                moduleIndexes = setOf(line.moduleIndex)
            )
        }

        if (line.tagType.usesFilter) {
            if (filter.whitelist && filter.isEmpty()) {
                issues += TagPolicyIssue(
                    severity = TagPolicySeverity.BLOCKING,
                    label = "Empty allow",
                    detail = "Whitelist mode with no item, tag, or mod rules blocks every item.",
                    moduleIndexes = setOf(line.moduleIndex)
                )
            }

            if (filter.matchMode == FilterMatchMode.ALL && activeFilterGroups(filter) > 1) {
                issues += TagPolicyIssue(
                    severity = TagPolicySeverity.WARNING,
                    label = "Strict match",
                    detail = "ALL mode requires the same item to satisfy every enabled filter group.",
                    moduleIndexes = setOf(line.moduleIndex)
                )
            }
        }

        return issues
    }

    private fun sharedIssues(lines: List<TagPolicyLine>): List<TagPolicyIssue> {
        val issues = mutableListOf<TagPolicyIssue>()

        for (firstIndex in lines.indices) {
            val first = lines[firstIndex]
            for (secondIndex in firstIndex + 1 until lines.size) {
                val second = lines[secondIndex]
                val modules = setOf(first.moduleIndex, second.moduleIndex)

                if (sameConcreteTarget(first, second) && filtersOverlap(first.spec.filter, second.spec.filter)) {
                    issues += TagPolicyIssue(
                        severity = TagPolicySeverity.WARNING,
                        label = "Same target",
                        detail = "These role cards point at the same target and may compete for the same work.",
                        moduleIndexes = modules
                    )
                } else if (filtersOverlap(first.spec.filter, second.spec.filter)) {
                    issues += TagPolicyIssue(
                        severity = TagPolicySeverity.INFO,
                        label = "Shared filter",
                        detail = "Another role card matches some of the same items, tags, or mods.",
                        moduleIndexes = modules
                    )
                }
            }
        }

        return issues
    }

    private fun sameConcreteTarget(first: TagPolicyLine, second: TagPolicyLine): Boolean {
        if (first.tagType != second.tagType) return false
        val firstSpec = first.spec
        val secondSpec = second.spec
        return when {
            firstSpec.boundPos != null && secondSpec.boundPos != null -> firstSpec.boundPos == secondSpec.boundPos
            firstSpec.boundArea != null && secondSpec.boundArea != null -> firstSpec.boundArea == secondSpec.boundArea
            else -> false
        }
    }

    private fun filtersOverlap(first: TagFilter, second: TagFilter): Boolean {
        if (first.isEmpty() || second.isEmpty()) return false
        if (!first.whitelist || !second.whitelist) return false

        val firstItems = itemIds(first.items)
        val secondItems = itemIds(second.items)
        if (firstItems.intersect(secondItems).isNotEmpty()) return true

        val firstTags = first.matchTags.toSet()
        val secondTags = second.matchTags.toSet()
        if (firstTags.intersect(secondTags).isNotEmpty()) return true

        val firstMods = first.matchModIds.toSet()
        val secondMods = second.matchModIds.toSet()
        if (firstMods.intersect(secondMods).isNotEmpty()) return true
        if (firstItems.any { it.substringBefore(':') in secondMods }) return true
        if (secondItems.any { it.substringBefore(':') in firstMods }) return true

        return false
    }

    private fun activeFilterGroups(filter: TagFilter): Int {
        return listOf(filter.items.isNotEmpty(), filter.matchTags.isNotEmpty(), filter.matchModIds.isNotEmpty()).count { it }
    }

    private fun itemIds(items: List<ItemStack>): Set<String> {
        return items
            .filterNot(ItemStack::isEmpty)
            .map { stack -> Registries.ITEM.getId(stack.item).toString() }
            .toSet()
    }

    private fun severityRank(severity: TagPolicySeverity): Int {
        return when (severity) {
            TagPolicySeverity.BLOCKING -> 0
            TagPolicySeverity.WARNING -> 1
            TagPolicySeverity.INFO -> 2
        }
    }
}