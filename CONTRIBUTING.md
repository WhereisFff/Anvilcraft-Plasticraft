# Contributing

Use JDK 21 (JetBrains Runtime 21 is also supported) and import the repository
directly as a Gradle project in IDEA.

Before opening a change:

1. Run `./gradlew runData` when registrations or resources change.
2. Run `./gradlew build` and, for gameplay changes, `./gradlew runGameTestServer` as appropriate.
3. Keep `package-info.java` nullness annotations in every Java package.
4. Keep optional JEI/Jade/Ageratum code in isolated integration packages so a
   client without those mods can still load the addon.
5. Do not commit `.idea`, `run`, `build`, or other machine-local output.

Follow the formatting rules in `style.xml` and describe gameplay changes in
English in commit and pull request titles. Chinese details may be included in
the body.
