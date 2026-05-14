# Changelog

## 0.2.16

- Cut native Command Post crews over to local work execution so spawned Party/PC workers are ticked directly by the Command Post through the existing tag engine, path budget, and worksite visual overlay flow.
- Added a native Command Post crew snapshot packet/cache/factory, giving the Crew tab local roster state with source location, missing-storage status, cargo, active state, assignment mode, and fallback settings.
- Added native crew row controls for return home, assignment mode, fallback, and drop actions without depending on the old pasture manager or Party/PC source row being present.
- Removed routine tag-behavior persistence from the pasture manager dirty-marker path so shared worker logic saves correctly for native Command Post workers.

## 0.2.15

- Added fallback identity data to native Command Post crew leases so missing or moved Pokemon remain visible in the crew roster instead of disappearing from management.
- Added native crew return-home networking and UI controls for sending a selected worker back to the Command Post without relying on a pasture manager action.
- Migrated old linked-pasture Command Posts into native crew leases on tick when a legacy roster is still present, then cleared the old link after successful migration.
- Reworded the Command Post home and crew UI around native Party/PC crews instead of linked-pasture setup states.

## 0.2.14

- Added native Command Post crew leases that remember each Pokemon's owner and Party/PC source slot, giving the rewrite stable metadata for routing and UI refreshes.
- Added Command Post crew send-out and recall lifecycle support so native crew can be materialized near the post for work and recalled when dropped or when the post is broken.
- Enriched Party/PC crew snapshots with live crew status, role assignment context, and cargo summaries so the native roster behaves like an active work roster instead of a static membership list.

## 0.2.13

- Added persistent native Command Post crew membership so Party and PC Pokemon can be added to or removed from a post without touching Cobblemon storage.
- Added Crew tab Add/Drop actions and a native roster view that shows assigned Command Post crew even before the spawn/recall lifecycle is finished.
- Let the router runtime see native crew membership and use crew Pokemon that are already present in-world, while leaving automatic PC/party summoning for the next lifecycle slice.

## 0.2.12

- Added a native Command Post crew-source foundation that reads the player's party and PC directly from Cobblemon storage.
- Reworked the Command Post Crew tab into a split roster/source view so current workers and assignable Party/PC candidates are visible in one polished surface.
- Added validated networking for Command Post crew-source snapshots without moving or spawning Pokemon yet, laying the safe groundwork for native crew assignment.

## 0.2.11

- Fixed worker navigation resolving solid job targets to the top of the target column or nearby high terrain, which could make pals climb hills with a job icon and then drop the job as if pathing had completed.
- Made unreachable job targets fail before starting movement when no adjacent safe standing spot exists, instead of handing a solid block position to Minecraft pathfinding.

## 0.2.10

- Added a shared pasture work-range leash so bound job targets, controller buffers, cached containers, and follow-up targets outside the active pasture range are rejected instead of sending pals across the world.
- Added emergency worker recall when a tagged pal has already wandered beyond its safe work area, preventing stuck pathing from continuing until the pal despawns.

## 0.2.9

- Replaced the in-world worker status cube with a compact status-colored backing behind the role icon, keeping worker state readable without hiding cargo or work items.

## 0.2.8

- Reworked worker movement through centralized navigation sessions with safe stand-position resolution, per-worker stuck tracking, and cached unreachable destinations.
- Added forward-hop recovery for stuck workers, so pals try to move and jump through common pasture-block lips before escalating to stronger recovery.
- Added bounded phase-reseat recovery that moves a stuck pal only to a nearby validated safe spot, then retries normal pathing or fails cleanly instead of standing forever.

## 0.2.7

- Cleaned up the Command Post Roles tab into a clearer card-list and detail flow, with roomier rows and a non-overlapping selected-role summary.
- Reduced the Roles detail actions to the most useful quick policy controls plus the full editor entry, making the page less crowded during setup.
- Moved the Command Post role-editor shortcut from `R` to `P` so recipe-viewer recipe lookup no longer intercepts the intended editor action.

## 0.2.6

- Added indexed worker-session lookups for pasture and Command Post ownership, replacing global scans when cleaning recalled pals or resolving router-managed crews.
- Centralized server-scale maintenance, global path-start budgeting, nearby-player caching, worker-visual throttling, and short-lived manager snapshots so large loaded bases spread their work more predictably.
- Bounded the heaviest fallback scans for harvest areas, distributor target discovery, and target-claim cleanup so oversized farms and container networks advance in slices instead of sweeping everything at once.

## 0.2.5

- Reworked the Command Post Roles view so every installed role card is its own policy line, making duplicate or multi-card setups editable and readable without collapsing them by role type.
- Added shared policy analysis for empty allow filters, overly strict ALL-mode filters, shared item filters, and duplicate targets so weak filter setups surface directly in Command Post labels and tooltips.
- Made role-card and filter edits invalidate the affected module immediately, so Command Post assignments and policy changes refresh on the next router tick instead of waiting behind the dispatch cooldown.

## 0.2.4

- Made Home the basic work surface by showing the player inventory there, so role cards can be inserted into the Command Post without switching to Buffer first.
- Cleaned up Command Post wording and status labels so role cards, crew state, boosts, and attention rows use shorter, clearer language.
- Fixed remaining Command Post text collisions by moving Crew controls down, widening the Roles detail column, and forcing action chips to stay inside their allotted space.

## 0.2.3

- Rebuilt the Command Post into a wider, quieter master-detail interface with compact tabs, persistent role slots, readable crew status, and separate Crew, Roles, and Buffer work surfaces.
- Reworked the pal detail, role policy, and pasture overview screens onto the same restrained visual system so backgrounds, buttons, slots, and status text now feel consistent instead of stitched together.
- Expanded cramped inventory layouts and removed striped filler, dense instructional copy, and overlapping drawer behavior so controls stay close to the thing they modify.

## 0.2.2

- Split the Command Post into a real five-surface shell with a dedicated Jobs board, a filterable and paged Crew roster, and in-shell drawers for pal detail and quick role-policy tuning instead of forcing every drill-in into detached screens.
- Added direct Command Post retuning for worker assignment mode and fallback behavior, plus inline role-policy quick controls for common filter, signal, target, run, and regulator changes while keeping the full ghost-slot editor available for deeper item-rule work.
- Shrunk the pasture surface into a compact local overview that focuses on presence, blocked and fainted alerts, and Command Post coverage so it no longer behaves like a second full admin console.

## 0.2.1

- Finished the Command Post UI rework into a calmer jobs-first hub with dedicated Home, Crew, Policy, and Buffer views, authoritative linked-pasture roster data, and direct row-based drill-in for both pals and role policies.
- Reworked the pal detail and role policy screens onto the shared CobblePals UI theme, added faster hover discovery, previous and next crew stepping, direct role-policy access from the pal inspector, and clearer tooltip language for role cards and augments.
- Demoted the pasture surface into a local overview, hardened filter editor loading and match-mode parsing, and removed the obsolete dedicated Pokemon-tag and tag-filter GUI textures from the live asset path.

## 0.2.0

- Added persistent crew identity controls to the Pokemon tag screen and worker session model, letting each pal be marked as preferred, restricted, reserved, or general with an explicit fallback policy that survives saves and feeds the same authoritative scheduler as the rest of the mod.
- Reworked pasture orchestration again so reserved pals stay out of general labor, restricted roles hold priority correctly, blocked or standby workers can surface in-world, and each pasture now applies a real navigation-start budget instead of letting large crews stampede pathfinding.
- Finished the audit's core completion pass by strengthening completion and blocked-state cues, surfacing crew identity in the manager, and updating the final-vision audit to distinguish the now-finished core product from optional post-core expansion ideas.

## 0.1.33

- Reworked the pasture manager into an operations board that now explains why each pal is ready, waiting, blocked, or on standby, and summarizes the current order mix so the screen behaves more like workforce control than a raw status dump.
- Added authoritative worker status reasons from the shared runtime so redstone stalls, no-target waits, eco scans, cooldowns, pathing trouble, and worker-cap standby all show up from the real server-side state instead of being guessed client-side.
- Reworked pasture worker scheduling so in-flight pals keep progressing and idle workers rotate fairly through limited worker slots, which reduces starvation from fixed list order and advances the audit's controlled-concurrency pass.

## 0.1.32

- Added active in-world worker overlays that now come from the same authoritative pasture tick state as the manager, giving busy pals a duty halo plus live tag and cargo icons instead of leaving activity readable only in menus.
- Added a nearby-player visual sync path for active workers so the world view and Command Post status board now reinforce the same phase and cargo truth without inventing a second execution model.
- Updated the final-vision architecture audit to record the shipped manager/status work, nearby-player slowdown policy, and this new Phase 3 readability slice.

## 0.1.31

- Reworked the pasture manager into a live status board: rows now refresh in place, sort active workers to the top, and expose real worker phase, cooldown, cargo, and Command Post linkage instead of a stale one-shot snapshot.
- Added proper tag and cargo icons plus clearer row summaries so it is much easier to read what each pal is assigned to, where it is heading, and whether it is actively carrying items.
- Added nearby-player pasture throttling controls so distant pastures stay on the same authoritative worker system while ticking more slowly when nobody is around to watch the work.

## 0.1.30

- Removed the dormant controller-native execution branch entirely so Command Post jobs now have one worker-led runtime instead of a half-dead second scheduler and UI state model.
- Simplified Command Post routing, pasture ticking, and status messaging around the real rule set: installed jobs need a linked pasture and active pals, with no hidden exception path.
- Kept the new session/spec architecture from 0.1.29 and finished the cleanup by deleting the unused router-native executor instead of leaving dead code behind.

## 0.1.29

- Rebuilt CobblePals' runtime authority around one shared worker-session store so assignments, carried inventories, and live worker state now travel through one canonical model instead of three parallel managers.
- Added a shared tag-spec layer and routed the Command Post, Pokemon assignment screen, and tag editor through it so tag config is no longer rebuilt field-by-field in different UI paths.
- Hardened slot-based tag editing with stack identity and revision checks, stopped saving Command Post worker-slot runtime into block NBT, and made controller-owned cleanup try to return carried items to the Command Post buffer before dropping anything.

## 0.1.28

- Moved CobblePals persistence onto one server-wide save authority so assignments and carried inventories no longer fight per-dimension state files or risk being wiped during shutdown.
- Made pasture worker bookkeeping dimension-aware, routed stale runtime pruning through shared cleanup, and fixed redstone-gated workers so they release claims safely while still finishing needed deposits.
- Locked the Pokemon assignment screen while a Command Post owns that pal, preventing managed tags or augments from being edited into a screen state that never applies.

## 0.1.27

- Fixed Puller job flow so pals carrying any extracted items always return to the Command Post buffer before trying to pull more, which avoids soft-lock behavior when filter settings change or mixed filter rules leave non-matching stacks in their inventory.
- Added clearer filter diagnostics to tag tooltips and the tag editor so item, tag, and mod filter groups plus their ANY/ALL semantics are visible while testing.
- Added a Command Post hotkey: hover a module card and press `R` to open that tag's editor directly from the router screen.

## 0.1.26

- Routed live Command Post tags back through the Pokemon worker loop so assigned pals visibly travel, work, and deposit instead of the router silently teleporting items or breaking blocks at range.
- Kept the Command Post buffer, bindings, and controller assignment flow intact while dropping the controller-native execution flag that made the work feel detached from the Pokemon.

## 0.1.25

- Removed the remaining pasture worker-slot dependency from controller-native Command Post modules, so router-executed tags no longer need assigned Pokemon records or consume linked pasture worker capacity.
- Let controller-native modules keep running even when no pasture is linked, while worker-managed tags still require a pasture link as expected.
- Updated Command Post runtime status so its counters and hints now describe runnable job slots instead of pretending every installed module needs a worker assignment.

## 0.1.24

- Moved Guardian onto the controller-native scheduler so hostile defense around a Command Post now runs as a bounded router-side strike pass instead of per-Pokemon pathing and target tracking.
- Moved Shepherd onto the same controller-native path so pen breeding now runs as a bounded controller pass using food from the Command Post buffer instead of autonomous worker inventory loops.
- Finished the current Command Post tag surface migration so every live controller-managed tag now executes inside the router scheduler rather than the old worker-first loop.

## 0.1.23

- Reworked Command Post factory modules so scalable tags now execute directly on the controller with bounded per-slot scheduler state instead of routing those jobs back through per-Pokemon worker AI.
- Moved Sender, Puller, Distributor, Dropper, Void, Breaker, Harvester, Vacuum, and Activator onto the new controller-native path while keeping existing module items, bindings, filters, and assignments world-compatible.
- Stopped controller-managed factory Pokemon from pathfinding or running worker state machines for those native tags, so large Command Post setups now scale around the controller scheduler instead of autonomous worker polling.

## 0.1.22

- Added a shared idle target-search retry gate so workers stop re-running expensive discovery scans every wake-up when no work is available.
- Reduced the hottest per-tag search costs by adding heavier idle backoff for Vacuum, Harvester, Shepherd, Guardian, and Activator, removing unnecessary entity-list sorting, and making Harvester resume large area scans from a rolling cursor.
- Tightened the performance defaults further by reducing pathfinding chatter, increasing container-cache minimums, and preserving old Courier/Stasher config values after the Sender/Distributor id migration.

## 0.1.21

- Promoted Sender and Distributor to real canonical item ids while keeping legacy Courier and Stasher items loadable and auto-normalizing them anywhere the mod actively touches those stacks.
- Tightened the tag filter UI so non-filter tags stop exposing ghost filter slots, filter rows, and stale tooltip data they no longer use.
- Reworked Activator into an explicit bound-target tool that right-clicks one selected block or entity cluster instead of scanning the surrounding area.

## 0.1.20

- Deleted the Smelter, Flinger, Player, Planter, Illuminator, and Weatherworker tags from the live CobblePals World surface, along with their behavior code and item resources.
- Reworked logistics around a modular-router-style split: Sender now pushes items out of the Command Post buffer, Puller now feeds that buffer from one bound source, and Distributor now spreads buffered items across multiple targets.
- Reworked Breaker into a one-block job that only mines its exact bound block, and changed Harvester to require an explicit player-selected work box instead of scanning arbitrary nearby farmland.

## 0.1.19

- Added behavior-owned target memory for Breaker, Guardian, Shepherd, and Weatherworker so workers keep working productive nearby targets instead of restarting full searches every cycle.
- Tightened Breaker's drop simulation by choosing a more appropriate tool for the block it is breaking, which makes its behavior closer to what players expect from real harvesting.
- Removed the leftover alias-only source texture definitions and art for sender, puller, placer, and distributor so the generated asset source now matches the live tag surface.

## 0.1.18

- Deleted the Fisher, Lookout, and Scout tags entirely from the live mod surface, including their behavior registration, recipes, models, lang keys, and source art assets.
- Reworked Activator into a simpler fake-player right-click tool that stocks matching items and performs predictable interaction passes without the old hidden mode system.
- Surfaced more tag settings directly in tag tooltips and the assignment screen, and added cached source-container lookups so common logistics tags do not re-scan from scratch every cycle.

## 0.1.17

- Refactored the shared tag worker lifecycle so arrival delay, arrival tolerance, and cooldown policy now belong to each tag behavior instead of living as hardcoded engine rules.
- Replaced the old breaker-only engine special case with a modular per-tag lifecycle contract, making CobblePals' worker execution model closer to the proven Cobbleworkers pattern without copying its code directly.

## 0.1.16

- Delayed the Command Post hover tooltips so they only appear after the mouse has rested on the same region for three seconds.
- Fixed the pasture manager send-home action to target the exact pasture shown in the open manager instead of depending on transient link state.
- Made breaker workers act immediately once they reach a target by skipping the normal arrival wait and using an effectively instant cooldown.

## 0.1.15

- Removed the long explanatory strings from the Command Post screen and moved that detail into hover tooltips on the relevant panels and counters.
- Tightened unbound breaker target selection so breaker workers ignore buried blocks and choose exposed matches instead, which fixes common grass-filter cases that never produced visible movement.

## 0.1.14

- Added a real 27-slot Command Post buffer and exposed it directly in the Command Post screen instead of forcing modules to rely on external containers.
- Reworked controller-run tag routing so producer tags deposit into the Command Post buffer by default and consumer tags pull from that same buffer when no explicit source is bound.
- Updated courier and stasher flows so the built-in Command Post buffer acts as the default logistics hub while still respecting explicit bound destinations and source containers.

## 0.1.13

- Reworked the Command Post screen styling again with a stronger control-console palette and clearer visual hierarchy.
- Replaced the old text-like filter controls with icon buttons and hover tooltips in the tag filter UI.
- Upgraded the pasture manager action controls to themed icon buttons with hover help for close and send-home actions.

## 0.1.12

- Rebuilt the Command Post screen into a wider, sectioned layout with readable labels, a proper jobs grid, a dedicated boost row, and cleaner pasture status reporting.
- Reworked the Command Post block art into distinct front, side, top, and bottom faces so it no longer renders like the same texture stamped on every side.

## 0.1.11

- Removed the square background fill from generated item textures so the outer area around tags and augments stays transparent in inventories and GUIs.
- Kept the AI-assisted frame styling inside the module silhouette while masking anything outside the intended chamfered shape.

## 0.1.10

- Added an InvokeAI-backed sprite prompt manifest and review log so the texture pipeline records which prompts worked, which failed, and why.
- Rebuilt the item and Command Post textures around reviewed AI-generated base frames plus crisp overlay glyphs, keeping the final art readable at Minecraft scale instead of shipping raw diffusion outputs.
- Refreshed the full CobblePals World icon set from the new hybrid pipeline so tags, augments, and the Command Post face all share a more cohesive style.

## 0.1.9

- Replaced the placeholder tag and augment icons with a full original texture suite so every current CobblePals World item now has dedicated art.
- Added a custom Command Post block texture so the block item no longer renders as a plain iron cube.
- Fixed stale item model mappings so flinger, player, and void now use CobblePals World textures instead of borrowed vanilla or legacy art.

## 0.1.8

- Rebalanced Command Post worker dispatch so new assignments rotate more fairly across the linked pasture roster instead of always front-loading the same first few Pokemon.
- Removed silent automatic relinking during runtime so a Command Post now stays unlinked until the player deliberately relinks it, preventing surprise pasture swaps.
- Polished Command Post feedback with exact pasture link coordinates in chat plus clearer screen hints for missing links, missing job cards, waiting jobs, and ready versus busy workers.

## 0.1.7

- Reworked the old router into a Pokemon-first Command Post that links to a nearby pasture and assigns installed job cards to real tethered workers instead of executing automation on its own.
- Added command-post assignment ownership so linked workers persist cleanly, release safely when the post is removed, and show as managed when opened in the per-Pokemon tag screen.
- Rebuilt the Command Post UI around linked pasture status, on-duty worker counts, and relinking controls while keeping the existing saved block id for world compatibility.

## 0.1.6

- Restricted router module slots to tags the router can actually execute and added an explicit unsupported-module warning for older saved routers.
- Added admin reset commands for runtime state and carried inventories, plus safe inventory shrinking when BST-based carry capacity drops.
- Normalized the remaining half-height search paths so pasture and router scans now use the same full-volume search behavior.

## 0.1.5

- Added a router block with its own inventory, module slots, upgrade slots, comparator support, hopper access, owner security, and ordered installed-module execution.
- Added router-native courier, stasher, dropper, flinger, void, player, planter, activator, vacuum, and lookout execution so installed tags can act independently of a Pokemon worker.
- Added dedicated flinger and void tags for the pasture worker system, plus router recipes, models, blockstate, loot table, and language entries.

## 0.1.4

- Added advanced tag settings for match-any/all filters, regulator amounts, redstone modes, activator action modes, and multi-target logistics routing.
- Upgraded courier and stasher behaviors with explicit target lists, target strategies, and cross-dimensional courier delivery for bound remote targets.
- Expanded planter into a general-purpose block placer, added activator attack behavior, and restored dedicated dropper and player delivery tags.

## 0.1.3

- Fixed pasture orphan pruning so missing workers now clean up and drop carried items before their saved assignment record is removed.
- Pruned unassigned carried inventories during periodic maintenance to keep stale runtime data from accumulating.
- Hardened lookout redstone output cleanup so detector tags track the actual placed power block, not the lamp's transient lit state.

## 0.1.2

- Fixed carried-item routing so vacuuming, extraction, and overflow handling no longer delete items when inventory capacity is tight.
- Persisted carried-item mutations immediately and pruned stale state, claims, and pasture-linked orphan records more aggressively.
- Reworked container searches to use full vertical range and grow worker inventories when BST-based capacity increases.
- Hardened pasture ticking by targeting a stable Cobblemon hook instead of a generated ticker lambda.
- Protected the filter screen's backing tag item from being moved mid-edit and removed the client-side fake Pokemon UUID sentinel.

## 0.1.1

- Fixed augment levels to stack correctly in the assignment UI and at runtime.
- Fixed tag binding so world-position tags and container-bound tags bind with the right rules.
- Fixed assignment edits to dirty persistent save data immediately.
- Fixed the filter screen to preserve hidden tag and mod-id rules on close.