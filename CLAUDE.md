# SkyCraft: notes for agents

Two halves: `skse/` (Skyrim SKSE plugin, C++, CMake + vcpkg) and `fabric/` (Minecraft Fabric mod, Java 25).
See `README.md` ("Building from source") and `docs/DESIGN.md`.

## Check the environment first

At the start of every session, and every time you switch branch, create a worktree or check out
something new, verify the environment before building or editing anything that needs it. If a check
fails, fix it (don't just report it), then re-run the check.

Worktrees are separate checkouts: gitignored and submodule content from the main checkout
(`C:\Users\Satsume\Skycraft`) is NOT there until you set it up.

1. **Submodule**: `skse/extern/CommonLibSSE-NG` must not be empty.
   Fix: `git submodule update --init --recursive`
2. **vcpkg**: `.tools/vcpkg/scripts/buildsystems/vcpkg.cmake` must exist (the CMake preset points at
   `../.tools/vcpkg`). In a worktree, link the main checkout's copy instead of cloning again:
   `New-Item -ItemType Junction -Path .tools -Target C:\Users\Satsume\Skycraft\.tools`
   In the main checkout, follow the README (`git clone https://github.com/microsoft/vcpkg .tools\vcpkg`, then bootstrap).
3. **C++ generator**: `skse/CMakePresets.json` asks for "Visual Studio 18 2026", but this machine has
   only Visual Studio 2022 Build Tools. Make sure the gitignored `skse/CMakeUserPresets.json` exists
   (create it if missing; it is a version-6 presets file with configure preset `vs2022` that
   `inherits` `default` and sets `generator` to `Visual Studio 17 2022`, plus build preset
   `release-vs2022` for it, configuration `RelWithDebInfo`) and use its presets:
   `cmake --preset vs2022` then `cmake --build --preset release-vs2022`.
   If CMake complains the generator "does not match the generator used previously", delete the gitignored `skse/build` and configure again.
   `tools/package.ps1` uses the `default` presets, so it fails here unless VS 2026 is installed.
4. **Java**: JDK 25 is at `C:\Program Files\Java\jdk-25.0.2`. `JAVA_HOME` is not set in agent shells,
   so set it before Gradle: `$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.2'`.
   Build the Fabric mod with `fabric\gradlew.bat build --offline` (the Gradle and Loom caches are warm).
5. **Git identity**: if `git commit` says the author is unknown, pass it for that command only
   (`git -c user.email=... -c user.name=... commit`) rather than changing git config.

Quick check:

```powershell
(Get-ChildItem skse\extern\CommonLibSSE-NG | Measure-Object).Count   # > 0
Test-Path .tools\vcpkg\scripts\buildsystems\vcpkg.cmake              # True
Test-Path skse\CMakeUserPresets.json                                 # True
Test-Path "$env:JAVA_HOME\bin\java.exe"                              # True (after setting JAVA_HOME)
```

## Conventions

- Mixins live in `fabric/src/main/java/dev/skycraft/mixin/` and must be listed in
  `fabric/src/main/resources/skycraft.mixins.json` (`defaultRequire` is 1, so a wrong target crashes
  at game load, not at compile time). Verify targets with `javap` on the deobfuscated jar in
  `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/`.
- Skyrim water, terrain and collision reach Minecraft through `SkyWater`, `SkyCollision` and `SkyClip`
  in `fabric/src/main/java/dev/skycraft/world/`; hook into those instead of adding parallel systems.
- Match the surrounding code's style (tabs in Java, short Javadoc on the "why").
