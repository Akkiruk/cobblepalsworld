# Command Post Native Crew System Audit

Date: 2026-05-14
Mod: CobblePals World
Scope: Audit and implementation plan only. No code changes are included in this document.

## Executive Summary

Yes, CobblePals World can stop depending on the Cobblemon Pasture Block for automation and recreate the needed behavior inside CobblePals itself. The correct target is a native Command Post crew system: the Command Post owns a roster, lets the player assign Pokemon from PC or party, spawns visible Cobblemon PokemonEntity workers near the Command Post, and drives the existing tag execution engine against those workers.

This is feasible, but it is not a small UI change. The pasture block currently provides roster storage, ownership, entity materialization, entity cleanup, player-facing selection, and a world anchor. CobblePals currently leans on all of that. Removing it means CobblePals must own the whole worker lifecycle instead of only reading `PokemonPastureBlockEntity.tetheredPokemon`.

The clean design is not to recreate a fake Pasture Block. It is to introduce a first-class `CommandPostCrew` model and then move the existing worker scheduler, snapshots, visual overlays, and assignment indexes from pasture-position vocabulary to Command Post/worksite vocabulary.

## Recommendation

Proceed, but implement it as a staged rewrite, not a patch. The current pasture dependency is deep enough that trying to shim a native roster behind old pasture names would preserve the same fragility and make future UI work harder.

Recommended end state:

- The Command Post is the only serious crew-management hub.
- Players assign workers from PC or party directly inside the Command Post.
- Assigned Pokemon are leased to exactly one Command Post at a time.
- CobblePals records each worker's original owner and storage coordinates.
- Workers are spawned with Cobblemon's normal Pokemon data via `Pokemon.sendOut(...)` or the safest available equivalent.
- Workers are recalled/despawned by CobblePals on unload, logout, block break, roster removal, death/faint, or invalid storage state.
- The existing tag execution engine remains the authority for movement, claiming, work, cargo, deposit, status, and overlays.

## Why Replace The Pasture Block Dependency

The current bug report fits a structural risk: if Cobblemon decides a Pasture Block tether is invalid, unloads its entity, recalls it, or returns it to the PC, CobblePals is downstream of that decision. CobblePals can try to recover after the fact, but it cannot make the Pasture Block stop owning the lifecycle.

Native Command Post ownership would remove that class of issue. Instead of asking "which Pokemon are currently tethered to this Cobblemon block?", CobblePals would ask "which Pokemon are leased to this Command Post, are they still valid in storage, and should they be spawned right now?"

That also matches the product direction better. The Command Post already wants to be the one-stop place for role cards, filters, worker policy, status, and logistics. Having a second required block with its own lifecycle makes the product less intuitive.

## Current Pasture Dependency Map

### Scheduler

`common/src/main/kotlin/com/cobblepalsworld/pasture/PastureWorkerManager.kt`

Current responsibilities:

- Ticks each Cobblemon Pasture Block.
- Reads `pasture.tetheredPokemon`.
- Calls `tethering.getPokemon()`.
- Requires `pokemon.entity` to be present.
- Skips fainted and sleeping Pokemon.
- Associates tagged Pokemon with a `PastureBinding`.
- Cleans up assignments if a tethered Pokemon disappears.
- Sends worker visuals keyed by `pasturePos`.
- Handles idle wander around the pasture.
- Handles cleanup when a pasture breaks.

Native replacement:

- Rename conceptually to `CommandPostCrewManager` or `CrewRuntimeManager`.
- Tick Command Post rosters, not pasture block entities.
- Resolve workers from Cobblemon storage by owner and recorded store coordinates.
- Ensure a visible entity exists when the worker should work.
- Supply `TagExecutionEngine.tick(world, entity, pokemon, tag, commandPostPos, budget)`.
- Clean up by lease validity rather than tether disappearance.

### Command Post Block Entity

`common/src/main/kotlin/com/cobblepalsworld/router/RouterBlockEntity.kt`

Current pasture-shaped state:

- `linkedPastureDimension`
- `linkedPasturePos`
- `hasLinkedPasture()`
- `linkedPasture(world)`
- `relinkToNearbyPasture(world)`
- `findNearbyPasture(world)`
- `linkedPastureAnchor()`
- persisted NBT keys `LinkedPastureDimension`, `LinkedPastureX/Y/Z`

Native replacement:

- Store a crew roster directly on the Command Post or in global save data keyed by Command Post position.
- Replace linked-pasture properties with crew status properties.
- Keep the old NBT read path for migration, but stop requiring a nearby pasture.
- Change `linkedPastureAnchor()` into a general `workAnchor()` or just use `pos`.
- Preserve inventory layout and module slots.

### Command Post Dispatcher

`common/src/main/kotlin/com/cobblepalsworld/router/RouterExecutionEngine.kt`

Current pasture dependencies:

- Calls `router.linkedPasture(world)`.
- Uses `linkedPasture.tetheredPokemon.size` as roster count.
- Uses `linkedPasture.pos` as cleanup/work anchor.
- Builds roster by iterating `pasture.tetheredPokemon`.
- Associates assignments with `TagAssignmentManager.associateWithPasture`.
- Releases workers when the linked pasture disappears.

Native replacement:

- Load the Command Post crew roster from a `CommandPostCrewManager`.
- Select candidates from leased crew entries instead of pasture tether entries.
- Use Command Post position as the work/deposit/home anchor.
- Do not clear assignments just because no pasture is nearby.
- Release workers only when removed from roster, Command Post is broken, storage is invalid, permissions fail, or the worker is assigned elsewhere.

### Assignment/Session Model

`common/src/main/kotlin/com/cobblepalsworld/pasture/TagAssignmentManager.kt`
`common/src/main/kotlin/com/cobblepalsworld/session/WorkerSessionManager.kt`

Current state:

- `PastureBinding(dimensionId, pos)`
- `ControllerBinding(dimensionId, pos)`
- Sessions can contain both a pasture binding and controller binding.
- There is a `pastureIndex` and `controllerIndex`.
- Orphan detection means "assignment says this Pokemon belongs to this pasture, but current tethered IDs no longer include it."

Native replacement:

- Replace `PastureBinding` with `WorksiteBinding` or `CrewBinding`.
- Collapse redundant concepts if possible: for Command Post-managed workers, the Command Post is both controller and crew owner.
- Keep assignment profile data: `GENERAL`, `PREFERRED`, `RESERVED`, fallback behavior.
- Add a separate lease/reservation model for Pokemon ownership and source store coordinates.
- Orphan detection becomes "lease exists, but Pokemon cannot be found in expected owner storage" or "entity exists but lease no longer exists."

### Persistent Save Data

`common/src/main/kotlin/com/cobblepalsworld/persistence/CobblePalsSaveData.kt`

Current persisted data:

- Assignments by Pokemon UUID.
- Optional `PastureDimension`, `PastureX/Y/Z`.
- Optional `ControllerDimension`, `ControllerX/Y/Z`.
- Assignment mode/fallback.
- Per-Pokemon cargo inventory.

Native additions needed:

- Command Post crew rosters keyed by dimension + position.
- Worker lease records keyed by Pokemon UUID.
- Owner UUID for each worker.
- Source store type: party or PC.
- Source store coordinates: party slot, PC box/slot, or Cobblemon `StoreCoordinates` if stable enough.
- Optional original display/species/level snapshot for UI fallback when storage resolution fails.
- Runtime-only entity UUID/entity id mapping should not be persisted as authoritative state.
- Migration data from old pasture-linked assignments.

### Runtime Scale Coordination

`common/src/main/kotlin/com/cobblepalsworld/runtime/ServerScaleRuntime.kt`

Current pasture vocabulary:

- `beforePastureTick`
- `pokemonPastureIndex`
- `rememberPastureMembership`
- `forgetPasture`
- `findPasturePos`
- snapshot, visual, nearby-player caches keyed by pasture position

Native replacement:

- Rename to worksite/Command Post vocabulary.
- Cache by Command Post position.
- Track `pokemonWorksiteIndex` or `pokemonCrewIndex`.
- Keep global path budget, visual throttling, nearby-player cache, snapshot cache, stale-state pruning.
- Maintain the same scalability protections; they are still useful.

### Networking

`common/src/main/kotlin/com/cobblepalsworld/networking/CobblePalsNetworking.kt`

Current pasture dependencies:

- `OpenManagerC2S(pasturePos)`
- `ManagerDataS2C(PastureSnapshot)`
- `WorkerVisualsS2C(pasturePos, visuals)`
- `TeleportHomeC2S(pasturePos, pokemonId)`
- Uses `PastureLinkManager.getLinkByPlayerId(player.uuid)`.
- Verifies `PokemonPastureBlockEntity` and `pasture.ownerName`.
- `sendSnapshotRefresh(pasturePos)`.

Native replacement:

- Add `OpenCommandPostCrewC2S(commandPostPos)`.
- Add `CommandPostCrewDataS2C(snapshot)`.
- Add `AssignCrewMemberC2S(commandPostPos, ownerUuid?, sourceType, sourceCoordinates)`.
- Add `RemoveCrewMemberC2S(commandPostPos, pokemonId)`.
- Add `RefreshAvailablePokemonC2S(commandPostPos, page/filter)`.
- Add `AvailablePokemonDataS2C(...)` for PC/party browsing.
- Change worker visuals to use `worksitePos` or `commandPostPos`.
- Change teleport home to target Command Post, not pasture.
- Stop using `PastureLinkManager` for Command Post UI access.
- Use `RouterBlockEntity.canAccess(player)` and owner UUID instead of pasture ownerName.

### UI Snapshot Layer

`common/src/main/kotlin/com/cobblepalsworld/gui/pasture/PastureData.kt`
`common/src/main/kotlin/com/cobblepalsworld/gui/pasture/PastureSnapshotFactory.kt`
`common/src/main/kotlin/com/cobblepalsworld/gui/pasture/PastureSnapshotCache.kt`

Current model:

- `PastureSnapshot(pasturePos, pals, maxWorkers, ownerName)`.
- `PalSnapshot` is built from `pasture.tetheredPokemon`.
- Snapshot cache keyed by pasture position.

Native replacement:

- Rename to `CrewSnapshot` / `CrewMemberSnapshot`.
- Snapshot includes Command Post pos, owner, roster capacity, assigned count, active count.
- `CrewMemberSnapshot` should include source location, lease status, entity status, assignment mode, role, status, cargo, target, and error state.
- Add available-PC/party snapshots separate from active crew snapshots.
- Keep current status/phase/cargo fields; they already align with native worker management.

### Command Post Screen

`common/src/main/kotlin/com/cobblepalsworld/gui/router/RouterScreen.kt`
`common/src/main/kotlin/com/cobblepalsworld/gui/router/RouterScreenHandler.kt`

Current state:

- Crew view requests `sendSnapshotRefresh(handler.linkedPasturePos)`.
- Crew data comes from `PastureSnapshotCache.get(handler.linkedPasturePos)`.
- Handler resolves crew rows by reading `router.linkedPasture(serverWorld)` and `PastureSnapshotFactory.create(...)`.
- Home and alerts still say things like "Link a pasture" and "Linked pasture is empty".

Native replacement:

- Crew tab becomes the assign/manage surface.
- Add a source browser: Party and PC tabs or segmented control.
- Add filters/search: name, species, type, level, status, already assigned.
- Add clear status for unavailable Pokemon: in battle, fainted, already leased, missing store record, no permission.
- Add assign/remove buttons and role preference controls in the same view.
- Remove all visible "linked pasture" language.
- Keep Home/Roles/Buffer split.
- Keep Crew as the place where players answer "who is working here?"

### Visual Overlay

`common/src/main/kotlin/com/cobblepalsworld/visual/WorkerOverlayRenderer.kt`

Current state:

- `replacePastureVisuals(pasturePos, visuals)`.
- Overlays are grouped by pasture position.
- Rendering itself is entity-id based and can survive the conceptual rename.

Native replacement:

- Rename to `replaceWorksiteVisuals(commandPostPos, visuals)`.
- Keep entity-id rendering and existing icon backing.
- Make stale cleanup happen per Command Post/worksite.

## What The Pasture Block Currently Provides

The pasture block is doing more than just keeping a list.

1. Roster

It owns the list of tethered Pokemon and exposes it through `tetheredPokemon`.

2. Storage lookup

Each tethering can resolve the real Cobblemon `Pokemon` data through `getPokemon()`.

3. Entity lifecycle

The tethered Pokemon generally has a live `pokemon.entity`, and the pasture system handles spawn/recall rules.

4. Ownership

The pasture has an `ownerName`, and CobblePals uses that for some manager actions.

5. Anchor

The pasture position is used for idle wander, cleanup, snapshots, visuals, nearby-player checks, and origin/leash behavior.

6. Player flow

The player already knows how to put Pokemon into a pasture through Cobblemon mechanics.

7. Unload/despawn behavior

This is also the problem area: Cobblemon owns the rules that decide whether the entity should remain near the pasture or return to storage.

## What CobblePals Must Recreate

### 1. Native Roster Persistence

A Command Post needs a persistent crew roster:

- Command Post dimension + position.
- Owner UUID and owner name snapshot.
- List of worker leases.
- Optional per-worker priority/order.
- Optional per-worker role preferences.
- Maximum active workers and maximum roster size.

A worker lease should store:

- Pokemon UUID.
- Owner UUID.
- Source type: party or PC.
- Source coordinates.
- Assigned Command Post binding.
- Lease state: active, missing, invalid, removed, awaiting recall, awaiting spawn.
- Optional last-known species/name/level for degraded UI.

### 2. Source Store Resolution

Implementation should use Cobblemon storage APIs:

- `Cobblemon.INSTANCE.getStorage()` returns `PokemonStoreManager`.
- `PokemonStoreManager.getParty(ownerUuid)` returns `PartyStore`.
- `PokemonStoreManager.getPC(ownerUuid)` returns `PCStore`.
- `PartyStore`, `PCStore`, and `PokemonStore` expose Pokemon lookup/iteration patterns.
- `Pokemon.getStoreCoordinates()` can identify where Cobblemon believes the Pokemon lives.

Known caveat: the quick signature inspection found these APIs in a nearby Cobblemon jar, but it reported a 1.20.1 Fabric jar. Implementation must verify exact signatures against the current 1.21.1 dependencies before coding.

### 3. Assignment From PC And Party

The Command Post UI needs to query the owner's party and PC.

Core rules:

- Only the Command Post owner, or an operator/admin, can assign from that owner's stores.
- A Pokemon can only be leased to one Command Post at a time.
- Fainted Pokemon can be shown but should not work.
- A Pokemon already sent out, in battle, traded, released, or moved into another invalid state should be blocked or recalled first.
- Party assignment must be explicit because party Pokemon are normally active gameplay resources.

Important product decision:

- Option A: assigning from party leaves the Pokemon in the party but marks it on duty. This feels convenient but is risky because Cobblemon battle/sendout interactions may conflict.
- Option B: assigning from party moves/reserves it into Command Post duty and treats it as unavailable for normal party use until removed. This is more reliable but needs careful UI copy.
- Option C: only PC Pokemon can be long-term workers, while party Pokemon can be quick-assigned by first moving them into the PC/crew lease. This is the safest persistence model.

Recommendation: support selecting from both PC and party, but internally treat every assigned Pokemon as a leased worker with a clear unavailable/on-duty status. If party conflict hooks are hard, start by requiring party workers to be recalled/not actively sent out and refuse assignment while they are battling or otherwise active.

### 4. Entity Spawn And Recall Lifecycle

Native crew requires CobblePals to create and remove visible Pokemon workers.

Likely API surface:

- `Pokemon.sendOut(world, Vec3d, ownerEntity)` can spawn a `PokemonEntity`.
- `Pokemon.entity` can locate an active entity when present.
- `PokemonEntity.recallWithAnimation()` can remove the entity and return to storage.
- `PokemonEntity.setQueuedToDespawn(...)` / despawner APIs may be available but should be used carefully.

Lifecycle rules needed:

- Spawn near the Command Post at a safe stand position.
- Do not spawn if the Command Post chunk is unloaded or no tick budget is available.
- Do not spawn if the source Pokemon cannot be resolved.
- Do not duplicate an entity if `pokemon.entity` already exists.
- If the entity exists far away and belongs to this lease, recall or teleport home depending on state.
- On Command Post unload, decide whether to recall all workers or leave active only while chunk remains loaded.
- On server stop, mark save data dirty, clean transient indexes, and let Cobblemon persist Pokemon normally.
- On Command Post break, recall all workers and release leases or keep roster in dropped item only if that product path is desired.

Recommendation: prefer recall on unload/break/release over silent entity killing. The worker is a real Pokemon; CobblePals should ask Cobblemon to recall it whenever possible.

### 5. Prevent Duplication And Store Drift

This is the most important safety area.

Risks:

- Same Pokemon assigned to two Command Posts.
- Pokemon moved in PC after assignment.
- Pokemon released/traded while leased.
- Party Pokemon used in battle while leased.
- Entity spawned twice because `pokemon.entity` lookup lags.
- Server restart while worker has cargo.
- Command Post removed while worker entity exists.

Required systems:

- Global `pokemonId -> commandPostBinding` lease index.
- Validation on every assign request.
- Validation during crew tick before spawning/ticking.
- A degraded UI state for missing/stale workers instead of deleting silently.
- Cargo recovery before release/removal.
- Admin cleanup command for impossible states.
- Migration-safe NBT versioning.

### 6. Worker Tick Integration

The existing `TagExecutionEngine` can remain the core engine because it already accepts:

- world
- `PokemonEntity`
- `Pokemon`
- `TagInstance`
- origin position
- navigation budget

The native crew manager simply needs to supply valid live entities and use Command Post position as origin.

Good news: recent navigation/scalability rewrites remain valuable. The hard part is getting the worker into the world safely and keeping its storage lease consistent, not rewriting every tag behavior.

### 7. Cargo And Inventory Recovery

Existing per-Pokemon cargo inventory persists in `WorkerSessionManager` and `CobblePalsSaveData`.

Native behavior must define:

- If worker is removed while carrying cargo, deposit to Command Post buffer if possible.
- If Command Post is missing/broken, drop cargo at safe nearby position or store it in a recoverable block/item state.
- If Pokemon source storage is missing, do not delete cargo.
- If Pokemon is recalled due to unload, retain cargo if the work session should resume later.

Recommendation: cargo belongs to the work session, not to the Cobblemon Pokemon storage. Continue persisting it in CobblePals save data.

### 8. Permissions And Ownership

Current pasture manager uses `ownerName` in places. Native Command Post should use UUID-first ownership:

- Command Post owner UUID from `RouterBlockEntity.ownerUuid()`.
- Owner name only as display fallback.
- Assignable stores belong to owner UUID.
- Operators may inspect or force release.
- Future multiplayer sharing can be added as explicit access lists.

### 9. Command Post UI Flow

Target first-screen flow inside the Command Post:

- Home: work health, roles demand, crew health, alerts.
- Crew: assigned roster plus Add Worker action.
- Add Worker modal/view: Party and PC source tabs, search/filter, assign button.
- Roles: role cards, policy, filters.
- Buffer: inventory.

Crew row should show:

- Pokemon name/species/level.
- Source: Party slot or PC box/slot.
- Status: Working, Ready, Fainted, Missing, On duty elsewhere, Needs recall, Storage moved.
- Assigned role/family.
- Assignment mode and fallback controls.
- Remove/release action.
- Teleport home/recall action.

Avoid making the old Pasture Manager a second primary surface. It can be removed or become a migration/debug-only path.

### 10. Migration

Existing worlds may have:

- Command Posts linked to pasture blocks.
- Assignments with `PastureBinding` and `ControllerBinding`.
- Workers carrying cargo.
- Role profiles configured for tethered Pokemon.

Migration path:

1. Keep reading old `LinkedPastureDimension/X/Y/Z` NBT.
2. On first tick/open after upgrade, if a linked pasture exists, read its tethered Pokemon.
3. Create native crew leases for those Pokemon under the Command Post owner if permissions allow.
4. Preserve existing role assignments and assignment profiles.
5. Preserve per-Pokemon cargo inventories.
6. Clear old linked-pasture state after successful migration.
7. If migration cannot resolve a Pokemon, create a missing worker entry only if there is meaningful saved state/cargo to recover.
8. Provide a visible migration warning in the Command Post UI if manual cleanup is needed.

### 11. Scalability

Native crew can be more scalable than the pasture-backed model if implemented around Command Post rosters:

- No scanning for nearby pasture blocks.
- No reliance on `tetheredPokemon` changing under us.
- Roster size is bounded per Command Post.
- Tick cadence can reuse existing stagger/distant slowdown.
- Pathfinding budget can remain global and per-worksite.
- Snapshot cache can be keyed by Command Post.
- PC/party browsing must be paged/filterable, not synced wholesale every tick.

Large-server rules:

- Do not scan all players' PCs globally.
- Only load/query a PC when a player opens an assignment view or a Command Post validates its own roster.
- Keep `pokemonId -> lease` reverse indexes in memory after save-load.
- Do not spawn workers for inactive/unloaded Command Posts.
- Distant Command Posts should tick slower or suspend visible workers if no players are nearby, depending on config.

## Proposed Architecture

### New Core Types

Suggested package: `com.cobblepalsworld.crew`

- `CommandPostCrewManager`
  - Runtime entry point for ticking, spawn validation, recalls, and snapshots.

- `CommandPostCrewRoster`
  - Persistent roster for one Command Post.

- `CrewMemberLease`
  - Persistent lease for one Pokemon.

- `CrewSourceRef`
  - Source owner and store coordinates.

- `CrewBinding`
  - Dimension + Command Post position.

- `CrewRuntimeState`
  - Transient entity id/UUID, last spawn attempt, last validation error, last seen tick.

- `CrewSnapshotFactory`
  - Builds UI snapshots from roster + worker sessions + Cobblemon storage.

- `AvailablePokemonSnapshotFactory`
  - Builds paged PC/party selection results.

Potential data classes:

```kotlin
data class CrewBinding(
    val dimensionId: String,
    val pos: BlockPos
)

data class CrewSourceRef(
    val ownerUuid: UUID,
    val sourceType: CrewSourceType,
    val box: Int? = null,
    val slot: Int,
    val storeCoordinatesNbt: NbtCompound? = null
)

data class CrewMemberLease(
    val pokemonId: UUID,
    val source: CrewSourceRef,
    val binding: CrewBinding,
    val assignedAtTick: Long,
    val displayNameFallback: String,
    val speciesFallback: String,
    val levelFallback: Int
)
```

Exact storage fields should be adjusted after verifying Cobblemon 1.21.1 API stability.

### Persistence Shape

Add a new save section:

```text
CommandPosts
  <dimension>|<x>|<y>|<z>
    Owner
    OwnerName
    Crew
      <pokemonUuid>
        SourceType
        Owner
        PartySlot / Box / Slot / StoreCoordinates
        DisplayName
        Species
        Level
        AssignmentMode
        AllowFallback
```

Keep existing `Assignments` and `Inventories`, but migrate assignment bindings away from `PastureBinding`.

### Tick Flow

1. Command Post block entity ticks.
2. `CommandPostCrewManager.beforeWorksiteTick(world, pos)` runs maintenance.
3. Load roster for this Command Post.
4. Validate leases cheaply; only deep-resolve storage on interval or if needed.
5. Resolve candidate Pokemon for role assignment.
6. Ensure selected workers have valid entities.
7. Call existing `TagExecutionEngine.tick` for active selected workers.
8. Update worker visuals keyed by Command Post pos.
9. Mark save data dirty only when meaningful state changes.

### Spawn Flow

1. Find safe spawn/home position near Command Post.
2. Resolve Pokemon from source store.
3. If `pokemon.entity` exists and is valid, use it.
4. If `pokemon.entity` exists but is far/invalid, recall/teleport depending on state.
5. If no entity exists and worker is schedulable, call Cobblemon send-out API.
6. Set navigation target/idle state near Command Post.
7. Record transient entity identity.

### Release Flow

1. Stop current work and release claims.
2. Deposit or preserve cargo.
3. Recall worker entity through Cobblemon when possible.
4. Remove assignment if requested.
5. Remove lease.
6. Clear reverse indexes.
7. Mark save data dirty.
8. Refresh Command Post snapshot.

## Implementation Phases

### Phase 0: API Verification Spike

Goal: prove exact Cobblemon 1.21.1 APIs before rewriting architecture.

Tasks:

- Verify `PokemonStoreManager.getParty(UUID)` and `getPC(UUID)` signatures.
- Verify party and PC iteration APIs.
- Verify stable source-coordinate model.
- Verify `Pokemon.sendOut(...)` signature.
- Verify recall/despawn APIs.
- Verify owner/battle/active-sendout state checks.
- Build a tiny internal debug command or temporary test harness only if needed.

Exit criteria:

- We know how to list party/PC Pokemon.
- We know how to resolve a stored Pokemon by saved coordinates.
- We know how to spawn and recall a worker without duping.

### Phase 1: Native Data Model Behind Existing UI

Goal: introduce crew leases and Command Post roster persistence without removing the pasture path yet.

Tasks:

- Add `crew` package and data model.
- Add save/load NBT for command post rosters and leases.
- Add reverse index `pokemonId -> CrewBinding`.
- Add migration helpers that can create leases from a linked pasture.
- Add admin/debug query command for leases.

Exit criteria:

- Save data round-trips cleanly.
- A Command Post can own a native roster in memory.
- Existing pasture automation still works during transition.

### Phase 2: PC/Party Selection Snapshots

Goal: make the Command Post able to show assignable Pokemon.

Tasks:

- Add C2S request for available Pokemon.
- Build paged Party/PC snapshots.
- Add server-side permission checks.
- Mark already leased Pokemon as unavailable.
- Add client UI for Add Worker from Party/PC.

Exit criteria:

- Player can open Command Post and see PC/party candidates.
- No worker spawning yet required.
- No huge PC payloads sent every tick.

### Phase 3: Native Worker Spawn/Recall Runtime

Goal: spawn native visible workers from leases.

Tasks:

- Implement safe spawn/home position resolution near Command Post.
- Resolve leased Pokemon from source storage.
- Spawn via Cobblemon API.
- Recall on release, break, unload, invalid state.
- Prevent duplicate active entities.
- Add missing/invalid status reasons.

Exit criteria:

- A leased Pokemon appears near the Command Post.
- It returns/recalls safely.
- Server restart does not duplicate or lose the Pokemon.

### Phase 4: Router Execution Cutover

Goal: run existing tags from native crew instead of pasture roster.

Tasks:

- Rewrite `RouterExecutionEngine.collectRoster` to use native crew.
- Replace `PastureBinding` assignment path with `CrewBinding`/Command Post binding.
- Use Command Post pos as origin anchor.
- Update active/assigned/roster status properties.
- Keep assignment profiles.

Exit criteria:

- Command Post roles dispatch to native crew workers.
- Existing movement/work/deposit behavior still works.
- No linked Pasture Block is required.

### Phase 5: UI Rename And Pasture Manager Retirement

Goal: remove pasture-facing product language and make Command Post the one-stop surface.

Tasks:

- Rename snapshots/cache from `PastureSnapshot` to `CrewSnapshot`.
- Replace "linked pasture" alerts with native crew alerts.
- Move crew assignment controls into Crew tab.
- Update teleport-home to recall/home-to-Command-Post.
- Keep role policy UI intact.

Exit criteria:

- User can manage crew entirely from Command Post.
- No UI flow requires using a Cobblemon Pasture Block.

### Phase 6: Migration And Removal

Goal: safely remove old pasture dependency from runtime.

Tasks:

- Migrate old linked-pasture Command Posts on first open/tick.
- Preserve cargo and assignment profiles.
- Remove/reduce `PastureWorkerManager` usage.
- Remove `PastureLinkManager` UI dependency.
- Leave compatibility readers for old NBT.
- Add cleanup command for old assignment bindings.

Exit criteria:

- Existing worlds upgrade without losing assignments or cargo.
- New Command Posts never search for a Pasture Block.
- CobblePals no longer imports `PokemonPastureBlockEntity` in core runtime/UI paths except migration compatibility.

## Validation Plan

### Unit/Integration Style Checks

- Save/load one Command Post roster.
- Save/load many Command Posts.
- Save/load worker lease from party source.
- Save/load worker lease from PC source.
- Move/remove Pokemon from source storage and verify missing state.
- Prevent assigning same Pokemon to two Command Posts.
- Preserve assignment profiles through migration.
- Preserve cargo through release/restart.

### In-Game Manual Validation

- Assign party Pokemon to Command Post.
- Assign PC Pokemon to Command Post.
- Remove worker and verify it returns to normal storage/entity state.
- Break Command Post with active workers.
- Restart server with active workers.
- Move far away and return.
- Chunk unload/reload.
- Worker fainted state.
- Worker in battle/sent-out conflict.
- Multiple Command Posts owned by same player.
- Multiple players with separate Command Posts.
- Operator access.

### Scalability Validation

- 100 Command Posts idle.
- 100 Command Posts with small crews.
- 1000 workers assigned, with only nearby posts actively ticking.
- PC browse with large PC box count.
- Visual update throttling with many active workers.
- Path budget under load.
- Snapshot refresh spam resistance.

### Regression Checks

- Breaker tag still works.
- Vacuum tag still works.
- Puller/sender/distributor/dropper still work.
- Activator still uses platform bridge correctly.
- Breeder/feeder still respects filters and inventory.
- Cargo deposits into Command Post storage.
- Role policy and fallback modes still behave.
- Worker overlays still render.

## Major Risks

### Cobblemon Storage Consistency

This is the highest risk. If CobblePals records store coordinates but the player moves the Pokemon in PC, the saved source can go stale. The implementation needs lookup by Pokemon UUID as a fallback, and UI should show when the source changed.

### Party Worker Conflicts

Party Pokemon are not passive storage entries. They can battle, follow, be sent out, faint, or be used by other Cobblemon systems. The safest first implementation may allow selecting from party but internally require a clear on-duty state and reject conflict states.

### Entity Duplication

Calling send-out when Cobblemon already has an entity for the Pokemon could duplicate or corrupt state. The runtime must always check `pokemon.entity` first and understand recall state.

### Chunk Unload Semantics

If a Command Post chunk unloads, CobblePals must decide whether workers recall immediately or suspend. Recalling is safer. Suspending is more immersive but harder to make safe.

### Migration Edge Cases

Old assignments may reference Pokemon no longer in the linked pasture, or cargo may exist for Pokemon not currently tethered. Migration should preserve recoverable data and show a clear missing state.

### API Drift Between Cobblemon Versions

The quick signature inspection found the relevant API shape in a nearby Cobblemon jar, but implementation must verify against the actual CobblePalsWorld 1.21.1 dependency. Do not code the full system until that spike is done.

## Open Design Questions

1. Should assigned party Pokemon remain in the party, or should Command Post duty make them unavailable until released?
2. Should workers recall when no players are nearby, or stay spawned while the chunk is loaded?
3. Should the Command Post have a roster size separate from active worker cap?
4. Should breaking a Command Post release all workers immediately, or should the dropped Command Post item preserve the roster?
5. Should admins be able to assign from other players' PC/party stores, or only inspect/release?
6. Should migration automatically convert every linked pasture Pokemon, or ask the player to confirm on first open?
7. Should native crew replace the old Pasture Manager screen entirely in the same release, or leave it as a temporary compatibility/debug view?

## Minimal First Version Definition

A reasonable first release of this rewrite should include:

- Command Post roster persistence.
- Assign from owner's party and PC.
- One global lease per Pokemon.
- Native spawn/recall near Command Post.
- Router execution against native crew.
- Existing role cards and cargo system preserved.
- Crew snapshot UI updated enough to manage add/remove/status.
- Migration from linked pasture assignments.
- No Pasture Block required for new Command Posts.

It should not try to add multiplayer sharing, advanced PC search, offline production, or hidden worker simulation in the same pass.

## Final Assessment

This rewrite is worth doing if the goal is for CobblePals World to be stable and intuitive long-term. The Pasture Block was a useful bootstrap, but it is now the wrong owner for worker lifecycle. The Command Post has become the real product surface, and the worker system should follow that architecture.

The core worker behavior does not need to be thrown away. The right move is to keep `TagExecutionEngine`, navigation, claims, cargo, role policies, and overlays, while replacing the roster/entity lifecycle layer underneath them.
