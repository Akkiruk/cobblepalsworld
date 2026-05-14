# Command Post Pasture UI Fidelity Matrix

Date: 2026-05-14
Scope: CobblePals World Command Post UI compared against Cobblemon Pasture Block / PC UI.

## Executive Decision

The current v0.2.24 Command Post screen is still a CobblePals dashboard with warmer colors. It does not yet look or behave like Cobblemon's Pasture Block UI.

The correct direction is not another paint pass. The correct direction is to port the real Cobblemon PC/Pasture UI structure into a Command Post-owned screen:

- Use Cobblemon's actual PC and pasture texture language, panel geometry, button states, slot overlays, scroll list structure, sounds, and Pokemon rendering patterns.
- Keep CobblePals backend ownership: Command Post crew snapshots, Party/PC source snapshots, tag policy, item buffer, and assignment packets.
- Do not reopen a separate Cobblemon PC menu as the primary workflow. The Command Post must be the one integrated surface.
- Treat any copied Cobblemon source/assets as MPL-covered files or references, with license notices restored.

In short: clone/port the Cobblemon UI shell and widgets, then wire them to CobblePals data.

## Legal / License Constraint

Cobblemon upstream is MPL 2.0. That permits copying, modifying, displaying, and distributing covered source/assets as part of a larger work, but the copied or modified covered files must remain available under MPL 2.0 and must preserve license notices.

Practical requirement for implementation:

- If we copy source code from Cobblemon, keep the MPL header on those derived files.
- If we copy Cobblemon textures into CobblePals, include a third-party notice and make the source form available.
- If we only reference `cobblemon:textures/...` at runtime, we are not redistributing those textures, but copied code still needs MPL treatment.
- Do not remove or hide Cobblemon license notices in derived files.

## Source Of Truth To Port

Upstream files inspected:

- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pc/PCGUI.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pc/StorageWidget.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pc/StorageSlot.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pc/IconButton.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pc/NavigationButton.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/ExitButton.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pasture/PastureWidget.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pasture/PasturePokemonScrollList.kt`
- `debug/cobblemon-upstream/common/src/main/kotlin/com/cobblemon/mod/common/client/gui/pasture/RecallButton.kt`

Core Cobblemon resources to reference or copy:

- `textures/gui/pc/pc_base.png`
- `textures/gui/pc/pc_screen_grid.png`
- `textures/gui/pc/pc_screen_overlay.png`
- `textures/gui/pc/pc_screen_glow.png`
- `textures/gui/pc/pc_slot_overlay.png`
- `textures/gui/pc/pc_pointer.png`
- `textures/gui/pc/party_panel.png`
- `textures/gui/pc/portrait_background.png`
- `textures/gui/pc/info_box.png`
- `textures/gui/pc/info_box_stats.png`
- `textures/gui/pc/pc_arrow_previous.png`
- `textures/gui/pc/pc_arrow_next.png`
- `textures/gui/pc/pc_icon_filter.png`
- `textures/gui/pc/pc_icon_options.png`
- `textures/gui/pasture/pasture_panel.png`
- `textures/gui/pasture/pasture_scroll_overlay.png`
- `textures/gui/pasture/pasture_slot.png`
- `textures/gui/pasture/pasture_slot_owner.png`
- `textures/gui/pasture/pasture_button.png`
- `textures/gui/pasture/pasture_slot_button.png`
- `textures/gui/pasture/pasture_slot_button_active.png`
- `textures/gui/pasture/pasture_slot_icon_move.png`
- `textures/gui/pasture/pasture_slot_icon_defend.png`
- `textures/gui/pasture/pc_slot_icon_pasture.png`
- `textures/gui/pasture/pc_slot_icon_move.png`
- `textures/gui/common/back_button.png`
- `textures/gui/common/back_button_icon.png`

## Fidelity Matrix

| Area | Cobblemon Pasture/PC UI | Current Command Post UI | Gap | Required Change |
| --- | --- | --- | --- | --- |
| Root screen | `PCGUI` is a centered 349x205 textured PC shell with dark grey molded chrome. It does not look like a vanilla inventory screen. | Large tan/brown handmade frame with a title header and two large stacked panels. | Entire root material is wrong. The current screen reads like a custom inventory dashboard. | Replace the root frame with a PC-style shell derived from `pc_base.png`, or a Command Post texture built by extending that shell's exact material language. |
| Primary structure | Cobblemon has left info, center PC grid, right Party/Pasture panel. The screen anatomy is stable and familiar. | Command Post has top tabs, top tag deck, lower work panel, two small grids, detail boxes. | Information architecture does not match the Pasture Block at all. | Rebuild around the Cobblemon three-column PC layout: left Command Post/selected Pokemon info, center Party/PC source grid, right Command Pasture roster. |
| Tabs | Cobblemon PC/Pasture has no big tab strip. Navigation is via box arrows, filter, option icons, and side panels. | Four Minecraft-style tabs: Post, Pasture, Tags, Items. | Tabs are the loudest sign that this is not Cobblemon UI. | Remove the tab bar from the main pasture workflow. Move Tags/Items into Cobblemon-style icon drawers or mode buttons using PC icon button textures. |
| Material palette | Grey metal frame, cyan/teal PC screen, dark side panel, pixel texture shading. | Beige/tan panels, brown outlines, colored accent strips. | Repaint made it warmer, not Cobblemon. | Stop drawing filled rectangles for core chrome. Use texture blits for frame, screen, side panels, rows, buttons, and overlays. |
| Center storage grid | `StorageWidget`: 174x155 screen, 5 rows x 6 columns, 25px slots, 2px padding, wallpaper, grid texture, overlay, glow. | Source grid is 6 columns x 5 rows, but cells are tiny 18px tan squares with colored initials. | Geometry is vaguely similar but visual and behavior are wrong. | Port `StorageWidget` and `StorageSlot` concepts. Use actual 25px Pokemon cells, screen grid, hover pointer, level/gender/held item overlays, and Pokemon model rendering. |
| Party display | Cobblemon party panel is the right side panel when not in Pasture mode, with 6 asymmetric 25px slots and release controls. | Party is a chip count plus the same small source grid. | Party does not feel like Cobblemon's party storage at all. | When source mode is Party, render `party_panel.png` style panel and exact party slot layout. When source mode is PC, render PC box grid. |
| Pasture display | `PastureWidget`: 82x169 `pasture_panel.png`, title, centered count, scroll list, recall button. | Left roster is a 4-column tan grid inside the lower panel. | The assigned roster is not a pasture panel. | Replace the assigned roster grid with a derived `CommandPostPastureWidget`: 82px or proportionally expanded pasture panel, count header, `PasturePokemonScrollList` rows, and recall/home/action buttons. |
| Pasture rows | `PasturePokemonScrollList`: 62x29 rows, owner variant texture, hover row variant, 3D model render, level, name, gender, held item, action icons. | Roster cells are square blocks with a fake initial avatar, level, and role glyph. | This is the biggest fidelity failure after the root chrome. | Port the pasture row renderer directly. Feed it Command Post crew DTOs instead of OpenPasture DTOs. Draw real Pokemon portraits/models using species/aspects. |
| Pokemon rendering | Cobblemon uses `drawProfilePokemon` with `FloatingState`, rotation, aspects, animation settings, scissor clipping. | CobblePals draws colored geometric avatars and species initials. | Current UI cannot ever pass as Cobblemon while fake avatars remain. | Use Cobblemon's render path for both source slots and roster rows. If only snapshots are available, extend snapshots with aspects, gender, level, held item, and enough render data. |
| Hover selection | Cobblemon uses pointer arrow texture `pc_pointer.png`, hover row state via texture vOffset, and preview Pokemon updates. | Active cells get bright borders and accent strips. | Interaction state language is custom. | Use Cobblemon pointer, row hover variants, and selected/grabbed slot visual language. |
| Add to pasture affordance | Cobblemon overlays `pc_slot_icon_move.png` on eligible PC/Party slots when hovered in Pasture mode. Already-pastured Pokemon get `pc_slot_icon_pasture.png`. | CobblePals uses Add/Drop text chips in a bottom detail panel. | The core action is in a text control, not in the Pokemon slot. | Move Add/Drop into slot overlays and row action icons. Keep detail buttons only as secondary controls. |
| Buttons | Cobblemon buttons are texture sprites with hover state via vertical atlas offset and PC click sounds. | CobblePals buttons are colored filled rectangles/text chips. | Buttons do not belong to the same UI family. | Port `IconButton`, `NavigationButton`, `RecallButton`, and `ExitButton` patterns. Use textures and Cobblemon sounds. |
| Box navigation | Cobblemon has tiny textured arrows at top of PC grid and a box name widget. | CobblePals uses page chips and tiny custom glyph buttons. | Source paging looks like a custom data table. | Use Cobblemon arrow buttons and a box label/header for PC source pages. Party should not paginate like PC. |
| Filtering | Cobblemon uses a bottom filter widget with `pc_icon_filter.png`, not a chip row. | CobblePals has Sort/Family/State/Mode chips. | Filter controls read like dashboard filters. | Build a Cobblemon-style filter drawer: bottom filter field for text, options icon for sort/family/state/mode. |
| Selected details | Cobblemon preview area has portrait background, model, type spacers, info boxes, stats, nature/ability/moves. | CobblePals has two small plain text panels at the bottom. | Detail presentation is much too flat. | Replace bottom detail boxes with a left selected-Pokemon preview/info column using `portrait_background.png`, `info_box.png`, `info_box_stats.png`, and Cobblemon scaled text. |
| Tag Cards | Cobblemon has no tag-card analog. | CobblePals puts Tag Cards in the top deck with item slots and status cards. | This is necessary CobblePals functionality, but it currently breaks the PC/Pasture illusion. | Rehouse tag cards as a Command Post module drawer in the left info column or an options subpanel. Use Cobblemon info box/panel textures, not handmade tan panels. |
| Augments | Cobblemon has no augment analog. | Purple augment slots sit below the tag card slots. | Functional but visually unrelated to Cobblemon PC. | Make augment slots a compact module strip styled like Cobblemon option buttons or info boxes. Avoid purple square slot wells unless the texture is redesigned in Cobblemon style. |
| Items/buffer | Cobblemon PC/Pasture does not show player inventory in the main pasture screen. | Command Post has an Items tab and item buffer/inventory surfaces. | Showing inventory slots in the pasture screen makes it feel like a generic block GUI. | Keep item buffer behind an icon drawer or separate mode within the same PC shell. Do not let item storage dominate the Pasture view. |
| Header/status | Cobblemon labels are integrated into panels: Box name, Pasture title, count. | Command Post has large header title/subtitle and EMPTY chip. | Header is Minecraft block-GUI language. | Fold status into the PC shell: title in box-name area, linked/empty as a small icon or panel badge, not a top inventory header. |
| Typography | Cobblemon uses scaled text helpers, bold labels, shadows on panel titles/buttons, DEFAULT_LARGE. | CobblePals uses vanilla `TextRenderer.drawText` almost everywhere. | Text rendering style is visibly different. | Port/use `drawScaledText`, `drawScaledTextJustifiedRight`, Cobblemon fonts, shadows, and scale conventions. |
| Scroll behavior | Cobblemon pasture list is an `ObjectSelectionList` with scissor clipping and `pasture_scroll_overlay.png`. | CobblePals grids use fixed paging. | Paging is not the Pasture Block behavior. | Use actual scroll list behavior for Command Pasture roster. PC boxes can page by box arrows; roster should scroll. |
| Empty states | Cobblemon empty state is mostly blank textured screen/panel, not explanatory dashboard text. | CobblePals writes `No pals yet`, `Select pal`, `Select Pokemon`, etc. in panels. | Too much explanatory text. | Reduce empty-state copy. Let empty textured slots/panels communicate most of the state. Use one compact label at most. |
| Sounds | Cobblemon uses PC click/grab/drop/release sounds. | Current controls are mostly normal UI clicks or no Cobblemon-specific sound language. | Audio feedback will still feel non-native. | Use `CobblemonSounds.PC_CLICK`, `PC_GRAB`, `PC_DROP`, and relevant release/recall sounds for Command Post UI actions. |
| Layout scale | Cobblemon's whole screen is compact: 349x205 internal UI. | Command Post is much taller and has two stacked major regions. | The silhouette does not match. | Target PCGUI dimensions first. If more space is needed, extend using side drawers that look like Cobblemon panels rather than making a tall block GUI. |
| Code ownership | Cobblemon widgets are concrete, textured, stateful components. | CobblePals has one large `RouterScreen.kt` with custom drawing helpers. | The current architecture encourages more facsimile drawing. | Create dedicated derived widgets: `CommandPostPcShell`, `CommandPostStorageWidget`, `CommandPostStorageSlot`, `CommandPostPastureWidget`, `CommandPostPastureScrollList`, `CommandPostIconButton`. |

## What The Screenshot Says

The current screenshot fails for these concrete reasons:

1. It is tan and flat while the target is grey metal plus cyan screen.
2. It has top tabs; the target has PC controls and side panels.
3. It uses fake square Pokemon avatars; the target uses actual Pokemon model rendering.
4. It uses data-dashboard chips; the target uses textured buttons, arrows, filters, and row icons.
5. It keeps assignment in text panels; the target puts pasture actions directly on Pokemon slots/rows.
6. It shows two equal grids; the target has one dominant PC grid and one narrow pasture list.
7. It explains itself with labels; the target trusts the visual affordances.
8. It has Minecraft inventory/block-GUI proportions; the target has Cobblemon PC proportions.

## Recommended Architecture

### New Screen Class

Create a new Command Post screen instead of continuing to stretch `RouterScreen.kt`:

- `gui/router/CommandPostPcScreen.kt`
- Owns the Command Post PC-style layout.
- Replaces the current tabbed screen as the default Command Post GUI.
- Uses CobblePals handler data and networking, not Cobblemon's PC storage packets.

### Derived Widget Set

Port/adapt these concepts from Cobblemon:

- `CommandPostStorageWidget`: center Party/PC source storage surface.
- `CommandPostStorageSlot`: source Pokemon slot renderer and click target.
- `CommandPostPastureWidget`: right Command Pasture roster panel.
- `CommandPostPastureScrollList`: assigned pal rows and scroll behavior.
- `CommandPostPastureSlot`: row renderer with model, name, level, item, action icons.
- `CommandPostIconButton`: texture-backed icon buttons using Cobblemon hover atlas behavior.
- `CommandPostRecallButton`: textured recall/home-all button equivalent.
- `CommandPostInfoPanel`: left preview/details/modules panel.

### Data Adapters

Do not make CobblePals depend on Cobblemon PCGUI state directly. Instead adapt existing snapshots into UI DTOs:

- `CrewSourcePokemonSnapshot` -> source storage slot DTO.
- `CommandPostCrewMemberSnapshot` -> pasture row DTO.
- Extend snapshots if needed for aspects, gender, held item, renderability, owner/source labels, active tag icon, and carried item summary.
- Keep assignment/drop/mode actions on CobblePals packets.

### Texture Strategy

Preferred implementation path:

1. Reference Cobblemon texture resources directly with `Identifier.of("cobblemon", "textures/gui/...")` while Cobblemon is a required dependency.
2. Copy and port only the minimum source widget code needed into CobblePals, preserving MPL headers.
3. If custom Command Post textures are needed, derive them from the Cobblemon PC/pasture textures and include MPL notice/source availability.

This gives visual fidelity without reopening a separate Cobblemon PC menu.

## Implementation Phases

### Phase 1: Shell And Asset Port

- Add a Cobblemon-style blit helper for Yarn `DrawContext`.
- Add resource constants for all PC/Pasture textures.
- Restore a third-party notice for MPL-derived UI source/assets.
- Build `CommandPostPcScreen` with Cobblemon PC shell dimensions and no top tab bar.

Acceptance:

- The empty Command Post screen silhouette should look like Cobblemon PC/Pasture before any CobblePals data is rendered.
- Screenshot comparison should show grey metal/cyan screen/right pasture panel, not tan panels.

### Phase 2: Source Storage Grid

- Port `StorageWidget` geometry: 174x155 screen, 30 PC slots, 25px slots, 2px gaps.
- Render Party in the Cobblemon party panel layout.
- Render PC boxes with Cobblemon box arrows and box label.
- Use real Pokemon rendering in slots.
- Add hover pointer and pasture/move overlay icons.

Acceptance:

- Party and PC source views look like Cobblemon storage, not CobblePals grids.
- Hovering an eligible Pokemon shows the same style of move/pasture overlay.

### Phase 3: Command Pasture Panel

- Port `PastureWidget` and `PasturePokemonScrollList` behavior.
- Render assigned Command Post pals as Cobblemon pasture rows.
- Use row action icons for return/drop/mode where possible.
- Use a textured bottom button for global recall/home behavior.

Acceptance:

- The right-side assigned roster is recognizably the Cobblemon Pasture panel.
- Assigned pals are rows with model/name/level/action icons, not square cards.

### Phase 4: Command Post Modules Inside The Shell

- Move Tag Cards and Augments into the left info/module column or an options drawer.
- Replace status cards/chips with Cobblemon info boxes and icon controls.
- Move sort/family/state/mode into a filter/options drawer.
- Keep Items behind an icon mode/drawer, not visible in the main Pasture surface.

Acceptance:

- Tags and items are available from the same menu, but they do not visually overpower the Pasture/PC structure.
- No tab-strip dashboard remains.

### Phase 5: Interaction Polish

- Add Cobblemon PC sounds for click/grab/drop/assign/drop.
- Add hover tooltip timing consistent with Cobblemon buttons.
- Add selected Pokemon preview in the left info panel.
- Verify Fabric and NeoForge builds.
- Deploy Fabric to CobbleverseMain and NeoForge to All the Mons.

Acceptance:

- The screen feels like a Command Post variant of Cobblemon PC/Pasture, not a themed inventory.

## Non-Negotiables For The Next UI Pass

- No top tab strip in the primary Pasture view.
- No fake initial avatars for Pokemon.
- No tan rectangle panels as the main material.
- No Add/Drop text chip as the primary assignment control.
- No separate `Open Cobblemon PC` bridge as the main answer.
- No large explanatory text blocks inside the screen.
- Use actual Cobblemon texture resources or MPL-derived copies.
- Use Cobblemon widget geometry first, then fit CobblePals systems into it.

## Final Recommendation

The current UI should be treated as a temporary backend-debug screen, not the product surface.

The next release should be a real replacement screen: a Command Post-owned fork of Cobblemon's PC/Pasture UI, adapted to Command Post data. That is the path that satisfies the user's requirement for the UI to look and act like the Pasture Block while keeping one integrated menu for Party/PC assignment, Command Pasture roster management, tag policy, and item logistics.