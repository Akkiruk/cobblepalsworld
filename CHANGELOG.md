# Changelog

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