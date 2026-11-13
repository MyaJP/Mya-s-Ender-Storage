# Mya's Ender Storage (Fork)

Color-coded ender chests and pouches that share their contents anywhere.
A Fabric fork of [EnderStorage](https://github.com/TheCBProject/EnderStorage) by covers1624, ChickenBones and Ecu, rewritten for Fabric on Minecraft 1.21.1 – 26.3.

| Loader | Minecraft                        | Needs      |
|--------|----------------------------------|------------|
| Fabric | 1.21.1 – 1.21.11, 26.1 – 26.3    | Fabric API |

- `fabric/<mc>/`: one small Gradle project per Minecraft version (`./gradlew -p 26.3 build` inside `fabric/`)
- `VERSION`: the mod version for every build
- `jars/`: the release jars; the Publish workflow uploads them to CurseForge

GitHub Actions builds every version on each push.

---

色の組み合わせで中身を共有できるエンダーチェストとエンダーポーチ。EnderStorage（covers1624・ChickenBones・Ecu）のFabric向けフォーク版です。Fabric API が必要です。

## License

MIT. See `LICENSE` (original EnderStorage © 2020 covers1624, ChickenBones, Ecu; fork © 2026 Mya).
