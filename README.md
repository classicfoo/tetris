# Tetris for Android

An original, offline Android Tetris implementation built with Kotlin, AndroidX, and Material Components. The game uses a pure Kotlin rules engine and a custom board renderer.

## Features

- Guideline-leaning play: 7-bag randomizer, SRS rotations, hold, five-piece preview, ghost piece, lock delay, T-spins, combos, back-to-back clears, perfect clears, and level progression.
- Official-style portrait gestures: distance-based left/right swipes, side taps for rotation, downward drag/drop, and upward hold.
- Compact HUD with hold/next previews, responsive board sizing, pause/settings menus, and accessible actions.
- Classic, Tengen Bevel, and Game Boy themes; grid and ghost-piece toggles; original MIDI music, sound effects, and haptics.
- Persistent settings and top scores. Active games intentionally restart after a new launch.
- No network access, ads, analytics, or account requirements.

## Build

```bash
./gradlew test
./gradlew lint
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install with Obtainium

Add the public GitHub repository `https://github.com/classicfoo/tetris` to Obtainium. Releases contain debug APKs intended for personal installation and upgrade through the same package/signing identity.

## License

AGPLv3. See [LICENSE](LICENSE).
