package com.cobblepalsworld.gui

import net.minecraft.client.gui.DrawContext

enum class UiGlyph(val pattern: Array<String>) {
    Close(arrayOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#")),
    Home(arrayOf("..#..", ".###.", "#####", ".#.#.", ".#.#.")),
    Check(arrayOf("....#", "...#.", "#.#..", ".##..", ".#...")),
    Ban(arrayOf(".###.", "#..##", "#.###", "##..#", ".###.")),
    Data(arrayOf("#.#..", ".###.", "#####", ".###.", "#.#..")),
    Filter(arrayOf("#####", ".###.", "..#..", "..#..", "..#..")),
    Bolt(arrayOf("..#..", ".##..", "#####", "..##.", "..#..")),
    Target(arrayOf(".###.", "#.#.#", "..#..", "#.#.#", ".###.")),
    Stop(arrayOf("#####", "#...#", "#...#", "#...#", "#####")),
    Cycle(arrayOf(".###.", "#....", "#.###", "#...#", ".###.")),
    Prev(arrayOf("..#..", ".##..", "#####", ".##..", "..#..")),
    Next(arrayOf("..#..", "..##.", "#####", "..##.", "..#..")),
    Plus(arrayOf("..#..", "..#..", "#####", "..#..", "..#..")),
    Minus(arrayOf(".....", ".....", "#####", ".....", ".....")),
    Action(arrayOf("#....", "##...", "###..", "##...", "#...."))
}

object UiIconButtons {
    fun contains(mouseX: Int, mouseY: Int, left: Int, top: Int, width: Int, height: Int): Boolean {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height
    }

    fun draw(
        context: DrawContext,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        glyph: UiGlyph,
        accent: Int,
        hovered: Boolean,
        active: Boolean = false
    ) {
        val border = CobblePalsUiTheme.buttonBorderColor(hovered)
        val body = CobblePalsUiTheme.buttonBodyColor(hovered, active)
        val inner = CobblePalsUiTheme.buttonInnerColor(hovered, active)

        context.fill(left - 1, top - 1, left + width + 1, top + height + 1, border)
        context.fill(left, top, left + width, top + height, body)
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, inner)
        context.fill(left + 1, top + 1, left + width - 1, top + 2, accent)
        context.fill(left + 1, top + 1, left + 2, top + height - 1, accent)

        val iconX = left + ((width - glyph.pattern[0].length) / 2)
        val iconY = top + ((height - glyph.pattern.size) / 2)
        for ((rowIndex, row) in glyph.pattern.withIndex()) {
            for ((columnIndex, cell) in row.withIndex()) {
                if (cell == '#') {
                    context.fill(iconX + columnIndex, iconY + rowIndex, iconX + columnIndex + 1, iconY + rowIndex + 1, accent)
                }
            }
        }
    }
}