# Verity Visuals

A client-side visual customization mod for Minecraft 1.21.11 using Fabric.

## Features

- **Block Outline** - customize the targeted block outline color and thickness.
- **China Hat** - show a configurable cone above your player in third person.
- **Tabbed menu** - animated HUD, Visuals, Effects, and Settings
  tabs with compact layout, hover feedback, and reset confirmation.
- **HUD catalog** - coordinates, FPS/ping, keystrokes, attack CPS, armor status,
  potion effects, health/food, target information, direction, and the original
  inventory HUD. Modules can be toggled, scaled, recolored, and repositioned in
  the HUD editor.
- **Improved Lighting** - client-side lightmap brightness adjustment; world
  lighting and gameplay state are not changed.
- **Player feedback** - configurable visual death transition and confirmed-hit
  indicator. No sound effects or third-party audio assets are included.
- **Auto Swap** - press a configurable key to select an exact item ID from the
  hotbar; it never searches or moves items in the main inventory.
- **No shader integration** - Verity Visuals does not bundle, download, or manage
  shader packs.
- **Localization** - English, Russian, Spanish, and French.

Settings, HUD positions, scales, module states, colors, and menu preferences are
stored in `config/verityvisuals.json`.

## Build

Run `.\gradlew.bat build` from the project directory. The remapped mod jar is
created under `build/libs`.

## Test in Minecraft

Install the built jar with Fabric Loader and Fabric API for Minecraft 1.21.11.
Join a world and press **Right Shift** to open or close the menu. Use the tabs
to enable HUD modules, drag them in the HUD editor, and adjust visual/effect
settings. Press **Escape** to close the menu. The China Hat is visible in
third-person view.
