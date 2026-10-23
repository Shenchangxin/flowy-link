---
name: flowy-link-native
description: UI and engineering rules for the Flowy Link native clients (iOS SwiftUI, Android Compose, HarmonyOS ArkUI). Use when designing or implementing mobile screens, tokens, pairing, or client architecture for Flowy Link.
---

# Flowy Link native

The phone is a full allo client. Commands go through `ExecutionHost`. Today the only host is the PC process. Screens never concatenate LAN URLs.

## Frameworks

Keep three native UI toolkits. Do not replace them with Flutter, React Native, Compose Multiplatform shared UI, or a WebView of the desktop SPA.

- iOS: SwiftUI, system font, 44pt minimum targets, grouped lists, sheets for confirmations.
- Android: Jetpack Compose, Material 3, 48dp minimum targets, system font, predictive back when the screen pushes.
- HarmonyOS: ArkUI, HarmonyOS Sans, 48vp minimum targets, bottom tabs, service widgets later.

Share tokens and module specs. Do not share widget trees.

## Visual

Accent comes from the desktop token `--flowy-attention` (`oklch(56% 0.055 250)`). Native code uses the sRGB stand-in in `design/tokens.json`. One accent per view. No gradients, no glow, no glass used as decoration.

Surfaces are flat system backgrounds. Spacing uses the 4-based scale in the token file. Text that is data (addresses, ports, fingerprints) uses tabular figures.

Empty, offline, and error states are designed. The disconnected screen has one primary action.

## Engineering

- UI calls the kernel command type, not HTTP, once the kernel binding exists. Until the binding is linked, screens may only render local connection state.
- Drafts, scroll position, and the last opened conversation stay on device.
- Projections are keyed by host fingerprint.
- Wake payloads never gain a text field. The cloud decoder rejects unknown fields.
- Strings that a person sees go through the platform string catalog. This first shell's catalog is the `copy` object beside each screen.
- New PC routes must be classified in `flowy-link-kernel` before merge.

## Review

Before finishing a screen, check: safe area, one accent, one primary action on empty states, error next to the action, targets at least 44pt, no gradient, no fake success when the host is unreachable.
