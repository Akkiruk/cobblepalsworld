"""Generate GUI texture PNGs for CobblePalsWorld."""
from PIL import Image, ImageDraw

# ─── Color palette (slightly warm, not pure gray — friendly/organic feel) ───
BG_DARK = (44, 44, 52)         # Main background
BG_PANEL = (56, 56, 66)        # Inner panel area
BG_SLOT = (33, 33, 40)         # Slot interior (dark)
BORDER_LIGHT = (128, 128, 148) # Light border (top/left bevel)
BORDER_DARK = (24, 24, 30)     # Dark border (bottom/right bevel)
BORDER_MID = (72, 72, 84)      # Mid border
SLOT_LIGHT = (70, 70, 82)      # Slot top-left highlight
SLOT_DARK = (22, 22, 28)       # Slot bottom-right shadow
ACCENT = (90, 160, 120)        # Green accent for status/headers
ACCENT_DIM = (60, 100, 80)     # Dim accent
HIGHLIGHT = (110, 90, 160)     # Purple/violet for augment area
SEPARATOR = (48, 48, 58)       # Subtle separator line
WHITE_DIM = (180, 180, 195)    # Text-area color suggestion
TAG_ACCENT = (180, 140, 60)    # Gold accent for tag slot
PLAYER_INV_BG = (50, 50, 60)   # Player inventory panel bg

def draw_outer_frame(draw, x, y, w, h):
    """Draw beveled outer frame (Minecraft-style container border)."""
    # Outer border
    draw.rectangle([x, y, x+w-1, y+h-1], outline=BORDER_DARK)
    # Inner bevels
    draw.line([(x+1, y+1), (x+w-2, y+1)], fill=BORDER_LIGHT)  # top inner
    draw.line([(x+1, y+1), (x+1, y+h-2)], fill=BORDER_LIGHT)  # left inner
    draw.line([(x+2, y+h-2), (x+w-2, y+h-2)], fill=BORDER_DARK)  # bottom inner
    draw.line([(x+w-2, y+2), (x+w-2, y+h-2)], fill=BORDER_DARK)  # right inner
    # Fill interior
    draw.rectangle([x+2, y+2, x+w-3, y+h-3], fill=BG_DARK)

def draw_slot(draw, x, y, size=18):
    """Draw a single inventory slot with bevel."""
    # Outer shadow (bottom-right bright, top-left dark = inset look)
    draw.line([(x, y), (x+size-1, y)], fill=SLOT_DARK)          # top
    draw.line([(x, y), (x, y+size-1)], fill=SLOT_DARK)          # left
    draw.line([(x+1, y+size-1), (x+size-1, y+size-1)], fill=SLOT_LIGHT)  # bottom
    draw.line([(x+size-1, y+1), (x+size-1, y+size-1)], fill=SLOT_LIGHT)  # right
    # Interior
    draw.rectangle([x+1, y+1, x+size-2, y+size-2], fill=BG_SLOT)

def draw_slot_grid(draw, startx, starty, cols, rows):
    """Draw a grid of inventory slots."""
    for r in range(rows):
        for c in range(cols):
            draw_slot(draw, startx + c * 18, starty + r * 18)

def draw_panel(draw, x, y, w, h, fill=BG_PANEL):
    """Draw an inset panel with subtle border."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=fill)
    draw.line([(x, y), (x+w-1, y)], fill=SLOT_DARK)
    draw.line([(x, y), (x, y+h-1)], fill=SLOT_DARK)
    draw.line([(x+1, y+h-1), (x+w-1, y+h-1)], fill=BORDER_MID)
    draw.line([(x+w-1, y+1), (x+w-1, y+h-1)], fill=BORDER_MID)

def draw_accent_line(draw, x1, y1, x2, y2, color=ACCENT_DIM):
    """Draw a thin accent/separator line."""
    draw.line([(x1, y1), (x2, y2)], fill=color)

def draw_label_area(draw, x, y, w, h, color=ACCENT_DIM):
    """Draw a subtle colored label background strip."""
    draw.rectangle([x, y, x+w-1, y+h-1], fill=color)

# ═══════════════════════════════════════════════════════════════
# POKEMON TAG SCREEN (176 x 186)
# Layout:
#   Row 0 (y=4-14): Title bar                     
#   Row 1 (y=16-70): [Augments 3x1] [Tag slot] [Display 3x3]
#   Row 2 (y=72-82): Status bar (phase, carrying, bound pos)
#   Row 3 (y=84-138): Player inventory 9x3
#   Row 4 (y=142-160): Hotbar 9x1
# ═══════════════════════════════════════════════════════════════

def generate_pokemon_tag_texture():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    W, H = 176, 166
    # Main frame
    draw_outer_frame(draw, 0, 0, W, H)

    # Title area: subtle accent strip
    draw_label_area(draw, 3, 3, W-6, 11, color=(50, 55, 64))

    # ── Left column: Augment panel ──
    # Label background for "AUG"
    draw_panel(draw, 5, 15, 22, 58, fill=(50, 46, 64))
    # 3 augment slots (vertically stacked)
    for i in range(3):
        draw_slot(draw, 7, 17 + i * 18)

    # ── Center: Tag slot (highlighted) ──
    # Gold-tinted backing behind tag slot
    draw_panel(draw, 29, 15, 24, 22, fill=(56, 52, 40))
    draw_slot(draw, 31, 17)
    # Small gold accent marks around tag slot
    draw.rectangle([29, 15, 52, 15], fill=TAG_ACCENT)

    # ── Right area: Pokémon carried inventory display (3x3) ──
    draw_panel(draw, 57, 15, 58, 58, fill=(46, 50, 56))
    # Display grid: 3x3
    # Row 1: 2 slots at top (items 0-1) aligned right
    draw_slot(draw, 59, 17)
    draw_slot(draw, 77, 17)
    # Row 2: 3 slots (items 2-4)
    draw_slot(draw, 59, 35)
    draw_slot(draw, 77, 35)
    draw_slot(draw, 95, 35)
    # Row 3: 3 slots (items 5-7)
    draw_slot(draw, 59, 53)
    draw_slot(draw, 77, 53)
    draw_slot(draw, 95, 53)
    # Extra slot (item 8) at top right
    draw_slot(draw, 95, 17)

    # ── Status panel (right side, compact) ──
    draw_panel(draw, 119, 15, 52, 58, fill=(46, 52, 50))
    # Phase indicator dot area (will be rendered by code)
    # Three info rows drawn by code: Phase, Carry, Bound

    # ── Separator line ──
    draw_accent_line(draw, 4, 75, W-5, 75, SEPARATOR)

    # ── Status text row ──
    # (rendered by code between separator and inventory)

    # ── Player inventory ──
    draw_panel(draw, 4, 80, W-8, 58, fill=PLAYER_INV_BG)
    draw_slot_grid(draw, 7, 82, 9, 3)

    # ── Hotbar ──
    draw_accent_line(draw, 4, 140, W-5, 140, SEPARATOR)
    draw_panel(draw, 4, 142, W-8, 20, fill=PLAYER_INV_BG)
    draw_slot_grid(draw, 7, 144, 9, 1)

    return img

# ═══════════════════════════════════════════════════════════════
# TAG FILTER SCREEN (176 x 166)
# Layout:
#   Row 0 (y=4): Title
#   Row 1 (y=16-70): [Filter grid 3x3] [Toggle buttons area]
#   Row 2 (y=72): Hint text area
#   Row 3 (y=84-138): Player inventory 9x3
#   Row 4 (y=142-160): Hotbar 9x1
# ═══════════════════════════════════════════════════════════════

def generate_tag_filter_texture():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    W, H = 176, 166
    draw_outer_frame(draw, 0, 0, W, H)

    # Title area
    draw_label_area(draw, 3, 3, W-6, 11, color=(50, 55, 64))

    # Filter grid panel (left/center)
    draw_panel(draw, 5, 16, 60, 58, fill=(48, 48, 58))
    draw_slot_grid(draw, 7, 18, 3, 3)

    # Filter mode panel (right of grid)
    draw_panel(draw, 69, 16, 102, 58, fill=(50, 50, 60))
    # Toggle button areas (will be drawn by code as actual buttons)
    # Whitelist/Blacklist button area
    draw_panel(draw, 72, 19, 96, 16, fill=(42, 42, 52))
    # Match NBT button area
    draw_panel(draw, 72, 38, 96, 16, fill=(42, 42, 52))
    # Match Tags button area  
    draw_panel(draw, 72, 57, 96, 14, fill=(42, 42, 52))

    # Hint text area
    draw_accent_line(draw, 4, 75, W-5, 75, SEPARATOR)

    # Player inventory
    draw_panel(draw, 4, 80, W-8, 58, fill=PLAYER_INV_BG)
    draw_slot_grid(draw, 7, 82, 9, 3)

    # Hotbar
    draw_accent_line(draw, 4, 140, W-5, 140, SEPARATOR)
    draw_panel(draw, 4, 142, W-8, 20, fill=PLAYER_INV_BG)
    draw_slot_grid(draw, 7, 144, 9, 1)

    return img

# ═══════════════════════════════════════════════════════════════
# WIDGETS SPRITESHEET (256 x 256)
# Layout:
#   Row 0 (y=0): Button backgrounds (16x16 each) — disabled, normal, hovered, pressed
#   Row 1 (y=16): Toggle button states — off-normal, off-hover, on-normal, on-hover
#   Row 2 (y=32): Phase indicator icons (8x8 each) — idle, navigating, working, depositing
#   Row 3 (y=48): Misc icons — whitelist, blacklist, NBT on, NBT off, tag on, tag off
#   Row 4 (y=64): Status bar backgrounds / progress indicators
# ═══════════════════════════════════════════════════════════════

def generate_widgets_texture():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Row 0: Standard button backgrounds (16x16 each)
    for i, (top, fill, bot) in enumerate([
        (BORDER_MID, BG_PANEL, BORDER_DARK),       # 0: disabled
        (BORDER_LIGHT, BG_PANEL, BORDER_DARK),      # 1: normal  
        ((150, 150, 170), (70, 70, 82), BORDER_DARK), # 2: hovered
        (BORDER_DARK, BG_SLOT, BORDER_LIGHT),        # 3: pressed (inverted bevel)
    ]):
        bx, by = i * 16, 0
        draw.rectangle([bx, by, bx+15, by+15], fill=fill)
        draw.line([(bx, by), (bx+15, by)], fill=top)
        draw.line([(bx, by), (bx, by+15)], fill=top)
        draw.line([(bx+1, by+15), (bx+15, by+15)], fill=bot)
        draw.line([(bx+15, by+1), (bx+15, by+15)], fill=bot)

    # Row 1: Toggle buttons (off/on × normal/hover) — 16x16
    off_colors = [
        (BORDER_LIGHT, (60, 50, 50), BORDER_DARK),  # off-normal (reddish)
        ((150, 140, 140), (75, 60, 60), BORDER_DARK), # off-hover
    ]
    on_colors = [
        (BORDER_LIGHT, (50, 70, 55), BORDER_DARK),  # on-normal (greenish)
        ((140, 160, 145), (60, 85, 65), BORDER_DARK), # on-hover
    ]
    for i, (top, fill, bot) in enumerate(off_colors + on_colors):
        bx, by = i * 16, 16
        draw.rectangle([bx, by, bx+15, by+15], fill=fill)
        draw.line([(bx, by), (bx+15, by)], fill=top)
        draw.line([(bx, by), (bx, by+15)], fill=top)
        draw.line([(bx+1, by+15), (bx+15, by+15)], fill=bot)
        draw.line([(bx+15, by+1), (bx+15, by+15)], fill=bot)

    # Row 2: Phase indicator dots/icons (10x10 each, at y=32)
    phase_colors = [
        (120, 120, 140),  # IDLE: gray
        (200, 180, 60),   # NAVIGATING: yellow
        (80, 200, 100),   # WORKING: green
        (80, 160, 220),   # DEPOSITING: blue
        (200, 80, 80),    # ERROR/ECO: red-ish
    ]
    for i, color in enumerate(phase_colors):
        cx, cy = 4 + i * 12, 36
        # Filled circle (3px radius)
        draw.ellipse([cx-3, cy-3, cx+3, cy+3], fill=color)
        # Bright center highlight
        r, g, b = color
        draw.point((cx-1, cy-1), fill=(min(r+60,255), min(g+60,255), min(b+60,255)))

    # Row 3: Mode icons (16x16, at y=48)
    # Whitelist icon (green checkmark-like mark)
    wx, wy = 0, 48
    draw.rectangle([wx, wy, wx+15, wy+15], fill=BG_SLOT)
    draw.line([(wx+3, wy+8), (wx+6, wy+11)], fill=(80, 200, 100), width=2)
    draw.line([(wx+6, wy+11), (wx+12, wy+4)], fill=(80, 200, 100), width=2)

    # Blacklist icon (red X)
    bx2, by2 = 16, 48
    draw.rectangle([bx2, by2, bx2+15, by2+15], fill=BG_SLOT)
    draw.line([(bx2+3, by2+3), (bx2+12, by2+12)], fill=(200, 80, 80), width=2)
    draw.line([(bx2+12, by2+3), (bx2+3, by2+12)], fill=(200, 80, 80), width=2)

    # NBT On icon (green curly bracket hint)
    nx, ny = 32, 48
    draw.rectangle([nx, ny, nx+15, ny+15], fill=BG_SLOT)
    draw.rectangle([nx+4, ny+3, nx+11, ny+12], outline=(80, 200, 100))

    # NBT Off icon (gray curly bracket hint)
    nx2, ny2 = 48, 48
    draw.rectangle([nx2, ny2, nx2+15, ny2+15], fill=BG_SLOT)
    draw.rectangle([nx2+4, ny2+3, nx2+11, ny2+12], outline=(100, 100, 110))

    # Row 4: Status bar background (128x6 at y=64)
    draw.rectangle([0, 64, 127, 69], fill=SLOT_DARK)
    draw.rectangle([1, 65, 126, 68], fill=(30, 30, 36))
    # Status bar fill template (128x6 at y=70) 
    draw.rectangle([0, 70, 127, 75], fill=ACCENT)

    return img

# ═══════════════════════════════════════════════════════════════

if __name__ == '__main__':
    import os
    base = r'c:\Users\rboon\curseforge\minecraft\Instances\CobbleverseMain\CobblePalsWorld'
    tex_dir = os.path.join(base, 'common', 'src', 'main', 'resources', 'assets', 'cobblepalsworld', 'textures', 'gui')
    os.makedirs(tex_dir, exist_ok=True)

    pokemon_tag = generate_pokemon_tag_texture()
    pokemon_tag.save(os.path.join(tex_dir, 'pokemon_tag.png'))
    print(f'Created pokemon_tag.png')

    tag_filter = generate_tag_filter_texture()
    tag_filter.save(os.path.join(tex_dir, 'tag_filter.png'))
    print(f'Created tag_filter.png')

    widgets = generate_widgets_texture()
    widgets.save(os.path.join(tex_dir, 'widgets.png'))
    print(f'Created widgets.png')

    print('All textures generated!')
