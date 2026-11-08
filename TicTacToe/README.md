# Neon Tic Tac Toe (Android)

Offline, ad-free tic-tac-toe inspired by the reference APK (`cpa.android.games.tictactoe`).
Pure Kotlin + Android framework views, no third-party libraries. minSdk 26, target/compile SDK 36.

## Build & run
1. Open this folder in **Android Studio** (current stable) and let it sync (accept the AGP/Gradle upgrade prompt if offered).
2. Pick a device or emulator (Android 8.0+, tested design target: Android 15/16) and press Run.
3. Command line: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`

## What it does
- Setup screen: vs Computer / vs Friend, Easy / Medium / Hard, player names, choose X or O.
- Game screen: live score cards (with turn highlight), draws counter, round counter, animated grid,
  strokes that draw themselves in, glowing win line, confetti, haptics, exit confirmation (back gesture / Menu).
- AI: Easy = random, Medium = wins/blocks but sometimes slips, Hard = perfect minimax (never loses).
- Starting player alternates each round. Edge-to-edge UI, predictive-back ready, adaptive + themed icon.

## Layout
- `Game.kt` – rules + AI (pure Kotlin, no Android imports)
- `BoardView.kt` – custom-drawn animated board
- `Ui.kt` – palette, glyph drawing, background, confetti, controls
- `MainActivity.kt` / `GameActivity.kt` – the two screens
