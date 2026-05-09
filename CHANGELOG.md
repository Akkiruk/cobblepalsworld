# Changelog

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