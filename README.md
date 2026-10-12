# Scribe | Book and Writing Mod

By Jaubert.

Write, format and read books comfortably in Minecraft. Books stay plain vanilla books, so they work on every server and for players without the mod.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3
- Java 25
- Optional: Mod Menu, for a settings button in the mod list

## Building

The jar is built by Gradle into build/libs/scribe-1.0.0+26.3.jar:

```
./gradlew build
```

## Project layout

- src/main: everything that also runs on servers (colored books, lecterns, signing with pen names and subtitles, editing on lecterns, reading animation, book info for look-at tooltips).
- src/client: the book screens, files, bookmarks, symbols, tooltips and rendering.
- libs/modmenu-api-stub.jar: two interfaces of Mod Menu's API, used only to compile. They are left out of every jar; the real ones come from Mod Menu when it is installed. Mod Menu is MIT licensed (libs/MODMENU-LICENSE.txt).

## Issues

Report bugs and ideas here:
https://github.com/jaubertt/Scribe/issues

## License

All rights reserved (see LICENSE).
