# UI Rules

## Main navigation

The application has five primary destinations:

1. Today
2. Recipes
3. Inventory
4. Meal log
5. Profile

Navigation and back behavior must be defined once by EF and reused by feature
owners.

## Shared visual system

- Keep theme, color, typography, spacing, radius, icon, and button rules in one
  shared source.
- Reuse shared title bars, buttons, cards, inputs, dialogs, date selectors,
  loading states, empty states, and errors.
- Do not create a second visual system inside a feature.
- Do not duplicate global theme definitions.

## Layout

- Support narrow windows and tablet-wide windows.
- Preserve state across portrait, landscape, split-screen, and window resizing.
- Keep text inside its container at supported font sizes.
- Account for keyboard visibility in forms.
- Test both empty and populated states.

## Pull request evidence

User-interface changes must include:

- the affected screens and states;
- narrow and wide layout screenshots;
- any known accessibility or responsiveness limitation.

Formatting tools cannot prove visual consistency. Shared components, design
tokens, screenshots, and acceptance checks are required.
