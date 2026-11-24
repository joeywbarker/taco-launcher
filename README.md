# 🌮 Taco Launcher

A home screen app for Android phones, made for the **GameSir Pocket Taco** clip-on controller.

Clip your phone into the Pocket Taco and Taco Launcher switches straight into a console-style
game menu. Everything fits in the part of the screen the Taco leaves visible, and you can
pick and play games using only the controller. Take the Taco off and you get an ordinary home
screen with all your apps.

> **No games are included.** Taco Launcher only shows and opens game files you already own.
> You also need an emulator app. **RetroArch** is recommended and gets the most built-in setup.

## Features

- **Taco mode turns on by itself** when the Pocket Taco connects, and can open the launcher for you.
- **Works on any phone size.** A first-run setup with a live preview asks how much of the screen
  the Taco covers. Menus, game windows and game pictures all fit above it.
- **Fully controller-driven.** Use the D-pad to move, A to pick and B to go back. You can teach
  it your buttons and map hotkeys like Exit game or Screenshot.
- **Your games, sorted by console.** Pick your games folder once and it sorts GBA, GBC, GB,
  NES, SNES, Genesis, arcade and more.
- **RetroArch set up for you each launch, with no RetroArch settings to change:**
  - the game picture sits above the Taco (including a "Tall" mode for upright arcade games);
  - Select + Start opens the menu;
  - Select + R1 and Select + L1 save and load your spot;
  - one game runs at a time, with a "Still playing?" prompt.
- **Per-system and per-game choices:** emulator, RetroArch core, screen layout and how names show.
- **Settings for everything:** each option is explained and says when it takes effect.
- **Backup & restore** for all your settings in one file.

## What you need

- An Android phone running Android 10 or newer.
- A GameSir Pocket Taco in **Android mode** (hold **M + A** for 2 seconds). Other controllers
  work too if you turn on "Treat any controller as a Pocket Taco".
- An emulator app. RetroArch is recommended, and you'll need to download the cores you want
  inside it (for example mGBA for Game Boy Advance).
- Your own game files.

## Install

1. On your phone, open the [latest release](../../releases/latest) and download the `.apk` file.
2. Open it. If Android asks, allow installing apps from your browser or files app.
3. Press Home, choose **Taco Launcher** and tap **Always**.
4. Follow the short first-run setup.

To switch back to your phone's own home screen, go to Settings → Apps → Default apps → Home app.

**Hotkeys** (Exit game, Select + Start and so on) need Taco Launcher's button helper turned on:
Settings → Accessibility → Installed apps → Taco Launcher. The app points you there when needed.

## Building it yourself

Every push to `main` is built by GitHub Actions (see `.github/workflows`), and the APK is
attached to a release. To build locally, open the project in Android Studio, or run
`gradle :app:assembleDebug` with JDK 17.

| File | What it does |
|---|---|
| `ControllerWatcher.kt` | Notices the Taco connecting (it shows up as "Gamesir -Pocket 1"). |
| `ui/LauncherRoot.kt` | Chooses the normal or Taco layout and splits the screen. |
| `ui/FirstRunSetup.kt` | The first-run setup with the live preview. |
| `ui/GameBrowser.kt` | The controller menus: systems, games, emulators. |
| `RomLibrary.kt` | Finds your games and sorts them by console. |
| `Emulators.kt` | Known emulator apps and how to hand each one a game. |
| `RetroArchPlacement.kt` | The per-launch RetroArch settings (picture position, hotkeys). |
| `ButtonMapperService.kt` | Hotkeys that work while a game is open. |
| `Settings.kt` / `ui/SettingsScreen.kt` | Your saved choices and the Settings screen. |
| `Backup.kt` | Backup & restore. |

## Not affiliated

Taco Launcher is a fan-made project. It isn't made by or connected to GameSir or the RetroArch
/ libretro team. All names belong to their owners.

## License

[MIT](LICENSE)
