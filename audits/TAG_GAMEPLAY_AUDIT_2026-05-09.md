# CobblePals World Tag Gameplay Audit

Date: 2026-05-09
Scope: player-facing usefulness, intuition, and obvious implementation/performance risks for every live tag behavior.
Method: code audit from real gameplay perspective. This is not a recorded in-game playtest.

## 2026-05-14 v0.2.31 Scope Update

- Activator default behavior is completed for this pass: normal Activator use is bound-block-first, while broader fake-player entity/in-air behavior requires a non-default target strategy.
- Short-lived search/cache concerns are partially completed by existing systems rather than a new tag-family cache layer: controller/container scans use `ContainerFinder.findControllerFirstCachedMatching`, Harvester uses a bounded search cursor, Guardian tracks active targets, and navigation uses throttling plus failure caches.
- Lookout, Scout, and Fisher are blocked/obsolete for the current product surface because those tags are not live in v0.2.31. Reintroducing them would add new tags, which is outside the corrected Command Post UI/existing-system scope unless explicitly requested again.
- Settings surfacing is now handled in the Command Post policy drawer and contextual side sheet for live tag cards.

## Executive Summary

- The strongest tags right now are Harvester, Vacuum, Smelter, Courier, Stasher, Planter, and Breaker. Their fantasy mostly matches what a player would expect.
- The weakest tags from a player-intuition standpoint are Lookout, Scout, Activator, and Fisher.
- The biggest systemic problem is not one tag. It is that many tags still rely on full-range block scans, full inventory scans, or full entity queries every work cycle.
- The biggest UX problem is hidden behavior. Several important rules live in the filter/settings screen, but the tag item tooltip and assignment screen do not explain them once the tag is in use.

## Highest Priority Findings

1. Lookout does not feel like a clean sensor.
   It says it emits a nearby redstone signal, but the current implementation places and removes redstone blocks near a lamp. Players are likely to read this as a detector-style output tag, not a world-editing lamp helper.

2. Scout does not produce reliable or actionable feedback.
   The fantasy is "find useful things nearby", but the implementation samples a random area, then spawns brief particles if it happens to hit something interesting. That will feel random, easy to miss, and hard to trust.

3. Activator is powerful, but too opaque for normal use.
   It can work well as an expert tool, but it is not intuitive for normal players because success depends on fake-player semantics, filter state, blacklist state, action mode, and target type. When something throws once, the item or block can be blacklisted until restart.

4. Fisher has a design/implementation mismatch.
   The comments describe finding a standing spot near water, but the target finder actually returns water blocks. Even if it "works", that is not the behavior the player fantasy suggests.

5. Performance will degrade sharply as active workers scale up.
   Multiple tags search by iterating whole cubes or querying whole entity sets at a 5-tick cadence. That is acceptable for one or two workers, but not for a busy pasture or multiple pastures running at once.

## Per-Tag Verdicts

### Strong

- Breaker
  Intuitive now that it only targets exposed matching blocks and respects safety bans. Still worth improving throughput and target caching, but the fantasy is understandable.

- Harvester
  Very strong. The fantasy matches the implementation and the special cases are reasonable for common farm blocks.

- Vacuum
  Very intuitive. Players understand what it does immediately and the carry-buffer behavior makes sense.

- Smelter
  Strong. "Pokemon-powered smelting" reads cleanly and the controller-buffer routing makes it practical.

- Courier
  Strong once configured. Source and destination semantics are much better after the controller-buffer work.

- Stasher
  Strong, especially with target lists and round-robin behavior. The main weakness is discoverability of its advanced settings.

- Planter
  Strong concept. Bound position as placement area is sensible and easy to explain.

- Void
  Clear concept and easy to use. The main risk is accidental deletion if the player does not realize the filter or source behavior.

### Good But Needs Better Player Signposting

- Guardian
  The concept is intuitive, but it feels generic rather than smart. It reads more like a simple melee sweep than a "guardian" with real threat logic.

- Dropper
  Mostly intuitive, but the difference between dropping at a bound position versus falling back to the pasture origin is easy to miss.

- Flinger
  The concept is understandable, but the firing-point rules are not obvious. Players may expect binding to define where it launches from, not just where it aims.

- Player
  Useful and understandable, but it depends heavily on owner proximity and hidden source-selection rules.

- Illuminator
  Mostly intuitive, but it is more limited than the name implies. It really means "place torch items in dark replaceable spots", not "light areas" in a broad or clever way.

- Shepherd
  The animal-breeding fantasy is easy to grasp, but the exact food/pen/source behavior is not obvious enough from the item alone.

- Weatherworker
  The idea is fine, but the effect is subtle enough that many players may not feel it working unless they watch crops for a while.

### Weak Or Misleading In Gameplay Terms

- Fisher
  Weak because it promises a fishing role, but its target-selection logic does not actually reflect a clean "sit by water and fish" loop.

- Activator
  Weak for non-expert players. It is too broad, too mode-dependent, and too failure-sensitive to feel intuitive without better explanation.

- Lookout
  Weak. The name and description imply a clean sensor, but the real behavior is a specialized container monitor that edits nearby lamp wiring.

- Scout
  Weak. It does not give the player a stable, navigable scouting result; it gives intermittent particles from random samples.

## Major Optimization Risks

- Full cube scans are everywhere.
  Breaker, Harvester, Fisher, Illuminator, Planter, Weatherworker, Scout, and Activator all scan space directly with iterateOutwards or controller/container sweeps.

- Container searches are naive.
  ContainerFinder performs linear search over all positions in range and then asks each block for an inventory. That is workable for small setups, but expensive for 16-block logistics tags across many workers.

- Several tags re-query entities every active cycle.
  Guardian, Vacuum, Shepherd, and Activator all perform fresh nearby entity scans instead of maintaining short-lived target caches.

- Eco mode only helps idle workers.
  Active workers still pay the full search and validation cost at the configured cadence.

## Major UX Risks

- Advanced settings are hidden after configuration.
  Target order, stop/loop, activator mode, regulator amount, and redstone mode are available in the filter/settings UI, but players cannot easily re-read those choices from the assignment screen or normal item tooltip.

- Some labels are technically correct but not decision-helpful.
  "No filter", "All", and simple carry counts do not explain the real operational rule for complex tags.

## Recommended Next Pass

1. Rework Lookout into a true redstone-output concept.
   Prefer powering a nearby output target or exposing a cleaner redstone hook rather than placing redstone blocks.

2. Rework Scout into deliberate scouting.
   Prefer stable marked targets, a scan memory list, or a visible "found X" result instead of random sampling particles.

3. Narrow Activator's default behavior.
   Make the common case predictable, then let advanced modes opt into the broader fake-player behavior.

4. Fix Fisher's targeting model.
   Make it choose a standable fishing edge or shoreline node rather than a water block.

5. Add short-lived search caches by tag family.
   The biggest scale win will come from reducing repeated full-range block and inventory searches.

6. Surface tag settings in the normal player-facing views.
   At minimum, show target strategy, redstone mode, activator mode, and stop/loop state on the item tooltip or assignment panel.