---
name: baseline-ui
description: Quickly deslop UI code by fixing spacing, hierarchy, typography, and small layout issues. Use when the interface needs a fast cleanup or polish pass.
---

# Baseline UI

Enforces an opinionated UI baseline to prevent AI-generated interface slop. Upstream: ibelick/ui-skills `baseline-ui`.

## How to use

Apply these constraints to interface work. For Flowy Link native screens, translate the interaction, typography, and design rules below into SwiftUI, Compose, or ArkUI. Do not introduce Tailwind, Radix, or a WebView to satisfy the web-specific stack lines.

## Stack

- Web surfaces use the project's existing primitives first.
- Native Flowy Link screens follow `flowy-link-native` for the stack. The rules below still apply.

## Interaction

- Destructive or irreversible actions require an explicit confirm step.
- Loading states use structural placeholders, not a lone spinner replacing the whole page.
- Fixed elements respect the safe area (notch, home indicator, gesture bar).
- Errors sit next to the action that failed.
- Never block paste in text fields.

## Animation

- Animate only transform and opacity.
- Do not animate layout size or position.
- Interaction feedback stays at or under 200ms.
- Honor reduced motion.
- Do not animate large blurs or full-screen surfaces.
- Do not introduce custom easing unless a platform default cannot express the motion.

## Typography

- Headings wrap as balanced text; body wraps as pretty text.
- Numbers that scan as data use tabular figures.
- Dense text truncates or clamps.
- Do not tighten or widen tracking unless the platform style explicitly requires it.

## Design

- No gradients unless explicitly requested.
- No purple or multicolor gradients.
- No glow as a primary affordance.
- Empty states have one clear next action.
- One accent per view.
- Use existing theme tokens before introducing a new color.
