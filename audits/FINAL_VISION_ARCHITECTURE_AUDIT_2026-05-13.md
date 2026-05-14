# CobblePals World Final Vision Architecture Audit

Date: 2026-05-13  
Scope: final-product vision, high-level architecture, planning priorities, and mission-aligned expansion strategy for CobblePals World.  
Method: direction audit based on the current project structure, prior architecture review, and clarified product goals from the owner. This document intentionally avoids code-level implementation guidance.

## Executive Summary

CobblePals World should end as a living workforce game first and an automation tool second. The final version should make players feel that their Pokemon are visibly helping them, that the Command Post is a clear management hub for directing that work, and that large setups scale by getting smarter about when and how workers act rather than by replacing worker behavior with hidden controller-side execution.

The most important architectural conclusion is that the mod should commit to a single authoritative worker model. Performance relief is allowed, and distance-based slowdown is acceptable, but the project should never solve scale by introducing a second silent system that guesses what workers would have done. The labor must remain real, even when it becomes less frequent, less detailed, or less visually rich while no player is nearby.

The second core conclusion is that readability is not polish. It is part of the product promise. If a player cannot glance at a Pokemon and understand what job it is doing, why it is doing it, and roughly what progress state it is in, then the feature is not finished. The final version therefore needs a full presentation layer for labor identity: particles, posture or animation cues, active job icons, obvious work targets, and for logistics roles, visible cargo cues when safe.

The third core conclusion is that CobblePals World should stay pasture-first. The project is strongest when it behaves like a workforce management fantasy with automation depth. It gets weaker when it drifts toward a general-purpose factory mod. The Command Post should remain the steering wheel for assigning, constraining, observing, and tuning pasture workers, not become a disguised machine block that performs work independently of them.

## Implementation Progress

The architecture direction in this document is no longer just aspirational. Several major slices are now live and should be treated as part of the working product baseline.

### Shipped Progress Through 0.1.32

- Phase 1 is functionally in place: worker runtime authority, persistence, assignment ownership, and carried inventory now run through one shared worker-session model instead of split controller and pasture truths.
- Phase 4 is partially delivered: the pasture manager now behaves like a live status board with refresh-in-place rows, real phase ordering, cargo visibility, cooldown visibility, and Command Post linkage instead of a one-shot config view.
- Phase 5 is partially delivered: nearby-player throttling now provides a real slowdown policy for distant pastures without introducing fake off-screen labor.
- Phase 3 is now partially delivered in-world as well: active workers can publish a live visual state to nearby clients so duty halos, role icons, and cargo cues reinforce the same worker truth shown by the manager.

### Still Missing Relative To Final Vision

- Blocked and idle explanation is still not a solved player-facing feature.
- Completion beats and family-specific effect language are still uneven across tags.
- Assignment identity, preference, and reservation tooling are still future work.
- Larger-orchard scalability still needs more explicit pathfinding and concurrency policy beyond the new distant slowdown baseline.

## Mission Statement

CobblePals World is a pasture-first Pokemon workforce mod where the Command Post directs real pals to perform visible, legible jobs in the world, and scaling comes from smarter orchestration and reduced distant activity rather than replacing Pokemon labor with hidden machine execution.

Short version:

CobblePals World makes Pokemon feel like a real working team, not a factory hidden behind themed blocks.

## Final Product Vision

The final version of CobblePals World should feel like this:

- A player has a pasture full of distinct workers whose jobs are obvious at a glance.
- The Command Post shows what each worker is assigned to, what each one is doing right now, who is idle, who is blocked, and why.
- Tags are not abstract cards with hidden side effects; they are readable role definitions that produce recognizable behavior in the world.
- Logistics tags look like logistics. Harvest tags look like harvesting. Defense tags look like defending. Support tags look like support.
- A player can scale their workforce without turning the mod into invisible backend automation.
- Optimization comes from better assignment rules, better task shaping, better work zones, and better orchestration, not from bypassing worker embodiment.
- The mod remains easy to direct, while still leaving enough expressive tools that smart players can build elegant, high-performing setups.

## Product Pillars

### 1. Living Workforce First

Pokemon should visibly move, target, act, carry, return, and react in ways that reinforce the fantasy that they are doing the work.

### 2. Command Post As Steering Wheel

The Command Post should be the player's workforce management center. It should assign intent, explain status, shape policies, and expose constraints. It should not become the true executor of labor.

### 3. One Authoritative Worker System

There should be one real truth about worker activity. Distance-based slowdown is acceptable, but hidden prediction, shadow simulation, or parallel machine logic is not.

### 4. Legibility Over Cleverness

Every tag must be obvious. Clarity beats technical elegance, feature breadth, or raw flexibility.

### 5. Expressive Simplicity

The mod should be easy to direct by default. Optimization should come from optional player tools, not from a complex baseline experience.

### 6. Scalable Orchestration

Scale should come from budgeted decision-making, reduced pathfinding churn, controlled concurrency, and calmer distant behavior, not from removing the workforce fantasy.

### 7. Pasture-Based Identity

Pasture automation with Pokemon remains the center of the mod. Any feature that weakens that center should be treated as suspect.

## Anti-Goals

The project should explicitly avoid the following outcomes:

- Turning the Command Post into a disguised machine that does the work itself.
- Introducing a second predictive or fallback system that simulates worker outcomes off-screen.
- Expanding into broad tech-mod territory that weakens the pasture-worker identity.
- Adding tags whose gameplay effect is useful but whose worker behavior is visually ambiguous.
- Prioritizing maximum throughput over visible, understandable labor.
- Growing content faster than the readability and management model can support.

## Gap Between Current State And Final Vision

### Where The Mod Is Already Strong

- The pasture plus Command Post framing already supports the right player fantasy.
- The tag-based role system provides a solid modular foundation.
- The project has a clear thematic niche that is stronger than generic automation.
- Recent stability work improved the trustworthiness of worker state and controller ownership.

### Where The Mod Is Still Weak

- In-world readability is not yet a universal standard.
- The Command Post still needs to mature as a management tool rather than a configuration surface.
- Worker assignment policy is not yet fully defined as a player-facing product feature.
- The scalability plan exists more as engineering instinct than as a formal gameplay policy.
- Some behaviors still risk feeling like systems prototypes instead of polished labor roles.

### Strategic Assessment

Conceptually the mod is strong. Architecturally it is improving. Product-wise it still needs a full pass that locks identity, standardizes job semantics, and builds presentation and scale policy around the clarified mission.

## High-Level Architecture Blueprint

The final architecture should be thought of as a stack of product systems rather than just gameplay code.

### Layer 1. Player Intent Layer

This is the surface where the player says what they want.

Responsibilities:

- Assign or remove jobs.
- Define work bounds, work targets, and logistics rules.
- Express priorities, preferences, and restrictions.
- Understand current status without reverse-engineering hidden logic.

Planning outcome:

The player should feel like they are giving orders to a workforce, not editing a logic spreadsheet.

### Layer 2. Workforce Identity Layer

This layer defines which Pokemon can do what, why, and under what preference rules.

Responsibilities:

- Determine baseline eligibility.
- Support player constraints and worker preferences.
- Preserve the idea that workers are individuals, even if the system remains easy to use.

Planning outcome:

The player should be able to let the system auto-assign valid workers while still having tools to influence who tends to do what.

### Layer 3. Assignment And Policy Layer

This is the rule engine that decides how work gets claimed and by whom.

Responsibilities:

- Translate job definitions into live assignments.
- Respect preference, restriction, reservation, and fallback rules.
- Avoid worker contention, duplicate targeting, and role confusion.
- Keep assignments stable enough to feel intentional.

Planning outcome:

The mod should feel reliable and understandable, not chaotic or over-optimized.

### Layer 4. World Execution Layer

This is the one authoritative system where actual labor happens.

Responsibilities:

- Drive movement, claiming, work, carrying, depositing, and return behavior.
- Maintain a single truth about worker state.
- Support slowing down, delaying, or deactivating distant activity without inventing fake results.

Planning outcome:

The labor remains real even when the simulation becomes lighter at distance.

### Layer 5. Presentation Layer

This layer sells the fantasy.

Responsibilities:

- Make job identity visible.
- Show what a worker is currently doing.
- Surface carried cargo and work-state signals where relevant.
- Reinforce the difference between tag families.

Planning outcome:

The player should understand the workforce by looking at it, not only by opening a menu.

### Layer 6. Performance Control Layer

This layer makes the game scale without betraying the fantasy.

Responsibilities:

- Budget active work.
- Reduce pathfinding pressure.
- Slow distant activity.
- Limit expensive scans.
- Preserve one authoritative truth.

Planning outcome:

Performance should degrade gracefully without changing what the system fundamentally is.

### Layer 7. Persistence And Authority Layer

This layer preserves trust.

Responsibilities:

- Keep assignments, inventories, and long-lived worker identity stable across reloads.
- Avoid duplicate authority or competing save surfaces.
- Ensure that the player's mental model survives world transitions and restarts.

Planning outcome:

The player should trust that their workforce comes back as they left it.

### Layer 8. Observability And Tuning Layer

This layer turns a complex system into a manageable one.

Responsibilities:

- Explain why a worker is idle or blocked.
- Expose the current assignment logic.
- Show bottlenecks and congestion.
- Support balancing and future content additions.

Planning outcome:

The Command Post becomes a useful manager interface rather than a mystery box.

## Workforce Model For The Final Version

The ideal workforce model is hybrid.

### Default Principle

Any qualified Pokemon should be able to perform a job so the system remains easy to use.

### Player Influence Principle

Players should be able to bias the system toward certain workers without being forced into rigid manual assignment for everything.

### Recommended End-State Capabilities

- Preferred workers for a role or job family.
- Restricted workers when the player wants exclusivity.
- Reserved workers that are held out of general labor.
- Optional fallback behavior when preferred workers are unavailable.
- Stable enough assignment behavior that a player's mental model remains intact.

### Why This Matters

This preserves both desired fantasies:

- the roster should "just work" with capable Pokemon,
- and players should be able to form identity around certain workers or job specialties.

## Command Post End-State

The final Command Post should feel like a real workforce console.

It should answer the following questions instantly:

- Who is assigned to which role?
- Which workers are active right now?
- Which workers are idle?
- Which workers are blocked and why?
- Which worker is carrying cargo?
- Which worker is reserved, preferred, or constrained?
- Which jobs are starved, over-contended, or under-supplied?

The Command Post should not merely configure tags. It should give the player confidence that they understand their operation.

## Tag Design Framework For The Final Version

Every tag should pass the same high-level test.

### A Finished Tag Must Answer These Questions Clearly

1. What kind of work is this?
2. What target does the worker seek?
3. What does the worker look like while doing it?
4. What does success look like in-world?
5. How can the player tell when the worker is blocked, waiting, or done?

### Tag Identity Rule

If two different tags look too similar while active, at least one of them is under-designed from a presentation standpoint.

### Tag Family Rule

The final version should think in job families, not only individual tags.

Useful families:

- Gathering and harvest roles.
- Logistics and cargo roles.
- Placement and shaping roles.
- Defense and area-control roles.
- Support and upkeep roles.

Each family should have a recognizable motion language, effect language, and feedback language.

## Presentation Strategy

Presentation is a core system, not optional polish.

### Required Presentation Outcomes

- A worker's role is visually recognizable.
- A worker's current activity state is visible.
- Logistics work visibly implies carrying or moving goods.
- The Command Post view and world view reinforce each other.

### Mission-Aligned Presentation Ideas

#### Duty Halo

An active floating icon above a worker while it is performing a job. This should appear during active work states rather than permanently, so it functions as an action marker rather than clutter.

Status: partially implemented. Active workers now broadcast a nearby-player overlay with a floating role icon and colored activity halo tied to the authoritative worker state. The remaining work is to deepen family-specific presentation and make blocked or completed states equally legible.

#### Signature Role Effects

Each job family should have a distinct effect language so the player can identify work from a distance without reading text.

#### Cargo Readout

Logistics workers should visually indicate that they are carrying something. If actual carried-item display is not always safe because some Pokemon already present held-item visuals, the final design should still provide a consistent fallback such as a cargo icon, crate glyph, or orbiting item proxy.

Status: partially implemented. Nearby clients now receive a cargo icon and item-count overlay for workers carrying inventory, which is enough to establish logistics identity without introducing fake held-item behavior.

#### Work Completion Moments

Each role should have a visible "job complete" beat. This gives labor emotional weight and makes the world feel responsive.

#### Worker State Visibility

Idle, traveling, working, returning, depositing, and blocked should each have a readable presentation strategy.

## Scalability Policy

The final version needs an explicit performance philosophy.

### Non-Negotiable Rule

Do not create a second predictive system that invents labor results when players are not nearby.

### Acceptable Slowdown Strategy

When players are distant or absent, workers may:

- act less often,
- path less often,
- scan less often,
- update effects less often,
- or go into a quieter, lower-intensity state.

What they should not do is secretly transition into a different truth model.

### Degradation Order

Based on the product goals, the first thing to budget aggressively should be pathfinding intensity. Pathfinding appears to be the most expensive part for the least fantasy return when overused.

After that, the mod can reduce:

- target search frequency,
- concurrent active worker count,
- non-essential visual richness,
- and distant update cadence.

### End-State Performance Principle

The world may become calmer at distance, but it should never become fake.

## New Unique Systems That Fit The Mission

The final version should not only stabilize the current systems. It should add a few product-defining ideas that make the workforce fantasy deeper and more unique.

### 1. Work Orders Board

A Command Post concept where the player is not just assigning tags but authoring priorities for the pasture. This makes the Command Post feel like management rather than slot configuration.

### 2. Preferred Role Certifications

Instead of hard manual micromanagement, players can mark Pokemon as favored for certain work families. This keeps the system easy to direct while giving identity to individual workers.

### 3. Reserved Crew Slots

Players can reserve certain pals for important work without having to hand-assign every role in the pasture. This is a clean bridge between automation and attachment.

### 4. Staging Pads

Special conceptual pickup and dropoff points around a Command Post or pasture that reduce route chaos, improve readability, and help make logistics look deliberate.

### 5. Work Lanes And Corridors

A player-facing way to encourage clean common travel paths, both improving spectacle and potentially reducing pathfinding churn.

### 6. Shift Modes

Simple operational modes such as normal, quiet, and burst. These give players easy macro control without requiring machine-like configuration.

### 7. Explain-Why-Idle View

A Command Post inspection feature that answers why a worker is not acting. This is one of the most valuable trust-building tools the mod can have.

### 8. Foreman Or Squad Concept

An optional layer where groups of workers can be organized into squads or work crews. This could add identity and management clarity without abandoning the pasture-first model.

### 9. Job Rehearsal Mode

A safe preview concept where a player can test whether a tag setup is understandable before they fully commit the workforce to it.

### 10. Living Downtime Behavior

Workers that are not active should still look like Pokemon waiting for work rather than frozen entities. Calm, low-cost idle behavior helps preserve the illusion that the pasture is alive.

## Roadmap To Reach The Final Version

The best route forward is staged. The mod should not chase every content idea at once.

### Phase 0. Identity Lock

Goal: establish firm product rules.

Deliverables:

- mission statement,
- design pillars,
- anti-goals,
- tag readability standard,
- performance philosophy.

Success condition:

Every new feature decision can be judged against the same product charter.

### Phase 1. Authoritative Worker Foundation

Goal: fully commit to one worker truth model.

Deliverables:

- stable ownership model,
- durable persistence authority,
- clear worker lifecycle,
- removal of any architecture that tempts controller-side fake execution.

Success condition:

The project has a stable foundation for future readability and scale work.

### Phase 2. Tag Semantics Standardization

Goal: make every role internally consistent.

Deliverables:

- shared expectations for target choice,
- carry semantics,
- deposit behavior,
- return behavior,
- blocked-state explanation.

Success condition:

The player can learn the system once and apply that knowledge across tags.

### Phase 3. Readability And Spectacle Pass

Goal: make the workforce obviously alive and understandable.

Deliverables:

- role identity cues,
- state cues,
- active halos or equivalent,
- cargo presentation for logistics,
- completion cues.

Success condition:

Watching the pasture teaches the player what is happening.

Current status:

- Active halos are now live.
- Role and cargo overlays are now live for nearby observers.
- Completion cues, blocked cues, and stronger family-specific spectacle are still pending.

### Phase 4. Command Post Management Maturity

Goal: transform the Command Post into a real control surface.

Deliverables:

- worker overview,
- blocked and idle explanations,
- role constraints and preferences,
- operational status visibility,
- work-order framing.

Success condition:

The player feels like they are directing a team.

Current status:

- Worker overview and operational visibility are now materially better through the live pasture manager board.
- Blocked and idle explanations plus true work-order framing are still missing.

### Phase 5. Scalable Orchestration Pass

Goal: preserve the workforce fantasy while making larger setups practical.

Deliverables:

- pathfinding budgets,
- calmer distant behavior,
- reduced search churn,
- controlled concurrency,
- predictable slowdown policy.

Success condition:

Larger pastures feel calmer and slower at distance, not fake.

Current status:

- Nearby-player slowdown is now live and aligned with the one-authority rule.
- Search churn, pathfinding budgets, and controlled concurrency still need a broader system pass.

### Phase 6. Assignment Identity Pass

Goal: let the player influence who does what without turning the mod into a micromanagement trap.

Deliverables:

- preferred workers,
- restricted workers,
- reserved workers,
- optional fallback policies,
- stable assignment behavior.

Success condition:

The roster both "just works" and supports attachment.

### Phase 7. Expansion And Differentiation

Goal: add distinctive systems that deepen the fantasy rather than broadening the scope aimlessly.

Deliverables:

- work orders,
- crew systems,
- shift modes,
- staging pads,
- route-shaping tools,
- deeper presentation polish.

Success condition:

The mod has a unique product identity that other automation mods do not replicate.

## Planning Principles For Future Features

Before any new feature or tag is approved, it should pass these tests:

1. Does it strengthen the pasture-worker fantasy?
2. Can a player understand it by watching it?
3. Does it belong under the Command Post management model?
4. Can it scale without inventing a second truth system?
5. Does it add expressive tools rather than just raw throughput?
6. Is it still obviously a Pokemon labor feature rather than a generic machine feature?

If a proposed idea fails several of these tests, it should be cut or redesigned.

## Success Criteria For The Final Version

The final version should be considered successful when the following statements are true:

- Players describe the mod as a Pokemon workforce mod, not a factory mod.
- Watching the pasture is enjoyable and informative.
- The Command Post answers most operational questions without outside explanation.
- Large setups remain believable because they become calmer, not because they become fake.
- Different tags are obviously different in-world.
- Players can let the system auto-assign workers, but also influence workforce identity when they want to.
- New content can be added without weakening the core fantasy.

## Key Risks

### Risk 1. Performance Panic

Under pressure, the project may be tempted to move labor back into invisible controller execution. That would solve short-term cost but damage the core identity.

### Risk 2. UI Overgrowth

As management improves, the Command Post could drift toward spreadsheet complexity. It needs to stay easy to direct.

### Risk 3. Content Outrunning Clarity

Adding many tags before the readability standard is complete will make the system feel noisy and uneven.

### Risk 4. Attachment Without Control

If workers feel personal but players cannot influence who does which work, the fantasy will feel underdeveloped.

### Risk 5. Control Without Attachment

If assignment becomes too rigid or too abstract, the roster may feel like generic worker slots instead of Pokemon.

## Final Strategic Recommendation

The project should spend its next major chapter not on breadth, but on identity completion.

The final version of CobblePals World should look like a calm, readable, living operation where the player manages a workforce of Pokemon through a strong Command Post, where every tag is obvious in-world, and where performance scaling preserves the fantasy instead of undermining it.

If the project stays disciplined about that mission, it can become something stronger than a themed automation mod. It can become a real workforce fantasy built around Pokemon behavior, player stewardship, and visible world labor.