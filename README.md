# Taco Launcher

A home screen app for Android (built for a Galaxy S26 Ultra) that rearranges
itself the moment a GameSir Pocket Taco connects.

## What it does

- **No controller:** a plain home screen with all your apps in a grid.
- **Pocket Taco connects:** it switches instantly to "Taco mode". Everything moves
  into the top ~70% of the screen. The bottom strip, where the Taco clamps on,
  is left black so nothing important hides under it.
- **In Taco mode you only need the controller:** D-pad to move, **A** to pick,
  **B** to go back. Browse Systems → Games → it opens the game in your emulator.
- **Settings:** a slider for how much screen the Taco covers (default 30%),
  choosing your games folder, a test switch to preview Taco mode without the
  controller, and a switch to treat *any* controller as a Taco.

## How the pieces fit

| File | Plain-words job |
|---|---|
| `ControllerWatcher.kt` | Listens for controllers connecting. Android announces this instantly, so no Bluetooth permission is needed. It recognises the Taco by name ("Gamesir -Pocket 1"). |
| `ui/LauncherRoot.kt` | Chooses normal layout or Taco layout, and does the top/bottom screen split. |
| `ui/GameBrowser.kt` | The controller-driven menus: systems, games, emulators. |
| `RomLibrary.kt` | Looks through your games folder and sorts games by console using the file ending (.gba, .sfc, …). |
| `Emulators.kt` | A list of known emulator apps (RetroArch, Lemuroid, My Boy!, melonDS, DraStic, PPSSPP, DuckStation…) and how to ask each one to open a game. |
| `Settings.kt` | Remembers your choices. |

## Things to know

- **Put the Taco in Android mode** (hold M + A for 2 seconds). Other modes give it
  a different name. If detection ever misses it, turn on "treat any controller
  as a Pocket Taco" in Settings.
- **The launcher only controls its own screen.** Once a game opens, the emulator
  draws the screen. Set each emulator to show the game in the top part of the
  screen (in RetroArch: Settings → Video → Scaling, or use a portrait overlay).
- **It switches when you're on the home screen.** If you're inside another app
  when the Taco connects, you'll see Taco mode the next time you press Home.
  Android blocks apps from jumping to the front on their own.
- **RetroArch** needs the matching "core" downloaded inside RetroArch first
  (e.g. mGBA for Game Boy Advance). Other emulators just receive the file.
- Not yet built or tested on a phone. See "Building it" below.

## Building it

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. Open this `taco-launcher` folder in it. It will download what it needs.
3. On the phone: Settings → About phone → Software information → tap
   **Build number** 7 times to unlock Developer options, then turn on
   **USB debugging**.
4. Plug the phone in and press the green ▶ Run button in Android Studio.
5. Press Home on the phone and pick **Taco Launcher**, then "Always".
   To go back to Samsung's home screen: Settings → Apps → Choose default apps →
   Home app → One UI Home.
