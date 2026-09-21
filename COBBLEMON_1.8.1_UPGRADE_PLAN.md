# Kế hoạch nâng cấp TC Living World cho Cobblemon 1.8.1

> Tài liệu bàn giao cho AI/code agent tiếp theo. Mục tiêu là tận dụng API và tính năng mới của Cobblemon 1.8/1.8.1 mà không phá save cũ, registry ID, cấu hình hoặc hành vi gameplay hiện có.

## 1. Phạm vi và nguyên tắc bắt buộc

- Nền tảng cố định: Minecraft `1.21.1`, Fabric, Java 21, Cobblemon `1.8.1+1.21.1`.
- Không đổi mod ID `tc_reborn`, namespace `tc_reborn`, package gốc `com.toancao.pokemonai`.
- Không nâng/hạ dependency khác nếu không cần để sửa lỗi compile.
- Ưu tiên API/event public của Cobblemon; không thêm reflection hoặc mixin mới nếu event/API giải quyết được.
- Mọi thay đổi attachment, codec, config và NBT phải đọc được dữ liệu cũ và có default an toàn.
- Không thay đổi tỷ lệ spawn, sức mạnh, tốc độ hoặc tần suất sự kiện ngầm trong một phase migration.
- Mỗi phase phải compile và test độc lập. Không gom tất cả phase thành một diff lớn.
- Tài liệu local `docs/cobblemon-1_8/` và dependency JAR 1.8.1 đang dùng trong Gradle là nguồn kỹ thuật chính xác nhất cho chữ ký API.

## 2. Trạng thái hiện tại đã xác minh

### Đã nâng cấp

- `gradle.properties` dùng `cobblemon_version=1.8.1+1.21.1`.
- `fabric.mod.json` yêu cầu Cobblemon `>=1.8.0 <1.9.0`.
- Project compile thành công bằng Java 21.
- Air spawner lấy bucket từ `Cobblemon.bestSpawner.config.worldBuckets`, không hard-code bucket weight.
- `PokemonEntity.setFlying(Boolean)` được dùng thay cho chỉnh trực tiếp `PokemonBehaviourFlag.FLYING`.
- Forced evolution dùng `Pokemon.initializeMovesetFromDefault()` thay cho `initializeMoveset(Boolean)` đã deprecated.
- Không còn chuỗi phiên bản `1.7.3` trong source hiện tại.

### Nợ kỹ thuật hiện tại

- `PokemonEntityMixin` inject vào `Mob.tick`, vì vậy callback chạy trên mọi mob rồi mới kiểm tra `instanceof PokemonEntity`.
- Natural herd hook dùng Fabric `ServerEntityEvents.ENTITY_LOAD` thay vì event chuyên biệt của Cobblemon.
- Mod có khái niệm `isAlpha` riêng, hiện chỉ đồng nghĩa với `HerdRole.LEADER`; điều này xung đột ngữ nghĩa với Alpha Pokémon native của 1.8.
- `GenericPackSpawner` tự tạo đàn, trong khi 1.8 có `PokemonHerdSpawnDetail` và hành vi herd native.
- `CustomAirSpawner` dùng pipeline selector của Cobblemon nhưng vẫn tự tạo `PokemonEntity`, đặt cloud và thêm entity; cần audit để bảo toàn event/influence/cap của pipeline.
- Project chưa có test source; task `test` hiện trả `NO-SOURCE`.
- `debug/check-syntax.js` hiện quét `0 files`, nên không thể dùng như validation chính.
- Compile còn warning ở `FlightConfigManager.kt:67` và nullability DataFixer trong `BlockRegistry.kt:40,44,48,52`.

## 3. Tính năng mới của Cobblemon 1.8 có liên quan

Cobblemon 1.8 bổ sung Alpha Pokémon, kích thước Pokémon, TM/Move Dex, Habitat Block và habitat structures. Alpha native lớn hơn, mạnh hơn, hung hăng hơn, luôn dẫn đàn và có TM move. Bản cập nhật cũng bổ sung cấu hình tắt spawn theo từng world và sửa lỗi Pokémon hiếm bị spawn lặp quá nhiều trong một chu kỳ. Nguồn: [Cobblemon Wiki – 1.8.0](https://wiki.cobblemon.com/index.php/1.8.0), [Cobblemon GitLab – changelog](https://gitlab.com/cable-mc/cobblemon/-/tree/main/changelogs).

Các thay đổi kỹ thuật đáng tận dụng:

- `Pokemon.isAlpha`/aspect alpha native và `Pokemon.scaleModifier`/`PokemonSizeCategory`.
- Herd spawning native qua `PokemonHerdSpawnDetail`.
- `PokemonEntity.getHerdSize()` và `adjustHerdSize(Int)`.
- Event lifecycle `COBBLEMON_INITIALISED`.
- Event entity/spawn `POKEMON_ENTITY_LOAD`, `ENTITY_SPAWN`, `SPAWN_BUCKET_CHOSEN`, `POKEMON_ENTITY_SAVE_TO_WORLD`.
- `moveset_builders` datapack và `initializeMovesetFromDefault()`/`initializeMovesetFrom(...)`.
- `RIDE_EVENT_PRE/POST` và nhiều Pokémon bay mới có thể cưỡi.
- Habitat events/API và spawn theo habitat.
- Các event evolution chính thức: `EVOLUTION_ACCEPTED`, `EVOLUTION_COMPLETE`.
- `held_item_visible`, TM events/data và advancement trigger mới.
- Herd native 1.8 đổi semantics: `levelRange` là pre-offset clamp, còn `herdLevelRange` mới dùng để filter level của đàn.

## 4. Kiến trúc đích

```text
Cobblemon lifecycle/events
        |
        +-- Pokemon entity load --> BehaviorApplicator (gắn goal đúng một lần)
        +-- Native spawn events --> HerdIntegration (không thay entity sau spawn)
        +-- Native alpha/herd ----> TC role metadata bổ sung
        +-- Native evolution ----> Dragon Gate policy/effect
        |
TC managers
        +-- FlightEngine: vật lý bay duy nhất
        +-- Herd behavior: formation/stampede/horn clash
        +-- World events: Dragon Gate + notices
```

TC Living World chỉ nên bổ sung hành vi mà Cobblemon chưa có. Dữ liệu alpha, kích thước, spawn bucket, herd count và lifecycle entity nên lấy từ Cobblemon.

## 5. Roadmap triển khai

### Phase 0 — Khóa baseline 1.8.1

Status: Done on 2026-09-21.

Mục tiêu: tạo một mốc compile sạch trước khi đổi hành vi.

Việc làm:

1. Ghi lại output của:
   ```powershell
   .\gradlew.bat compileKotlin compileJava --rerun-tasks --no-daemon
   .\gradlew.bat test --rerun-tasks --no-daemon
   ```
2. Sửa warning `FlightConfigManager.kt:67` nếu chỉ là null-check/condition thừa và không đổi kết quả.
3. Sửa bốn warning nullability trong `BlockRegistry.kt` bằng kiểm tra/fallback typed; không dùng `!!` nếu registry lookup thực sự nullable.
4. Sửa `debug/check-syntax.js` để nhận `.kt` và `.java`, hoặc ghi rõ bỏ script này và dựa vào Gradle.
5. Không chạy `build` ở phase này vì task được `finalizedBy(copyToDownload)` và có side effect ngoài repo.

Tiêu chí nghiệm thu:

- Compile thành công, không còn warning vừa nêu.
- `git diff --check` sạch.
- Không có thay đổi gameplay hoặc resource registry.

Completion notes:

- `FlightConfigManager.kt`: JSON maps for V3 flight config are now nullable at load time, so broken or partial config files fall back to migration/default handling without Kotlin always-true warnings.
- `BlockRegistry.kt`: `BlockEntityType.Builder.build(null)` is wrapped in one local helper with a narrow nullability suppress.
- `debug/check-syntax.js`: syntax scan now targets `src/main/java` and includes `.kt` / `.java`; latest run scanned 93 files.
- Verification: `node debug/check-syntax.js` passed; `.\gradlew.bat compileKotlin compileJava --rerun-tasks --no-daemon` passed with no warnings.

### Phase 1 — Chuyển khởi tạo sang lifecycle Cobblemon

Status: Done on 2026-09-21.

Mục tiêu: chỉ đăng ký logic phụ thuộc Cobblemon sau khi Cobblemon đã initialize.

File dự kiến:

- `PokemonAIMod.kt`
- Có thể thêm `compat/CobblemonLifecycle.kt` nếu `PokemonAIMod` trở nên khó đọc.

Việc làm:

1. Giữ đăng ký registry Fabric bắt buộc ở `onInitialize()`.
2. Chuyển phần cần `PokemonSpecies`, spawner config, event Cobblemon hoặc registry Cobblemon vào callback `CobblemonEvents.COBBLEMON_INITIALISED`.
3. Callback phải idempotent: có guard để không đăng ký hai lần trong test/dev reload.
4. Không truy cập `Cobblemon.bestSpawner.config` trước lifecycle phù hợp.

Tiêu chí nghiệm thu:

- Dedicated server khởi động không lỗi thứ tự init.
- Behavior, flight registry và event subscriber chỉ được đăng ký một lần.

Completion notes:

- `PokemonAIMod.kt`: Fabric/block/network/config registration stays in `onInitialize()`.
- Cobblemon-dependent integrations are isolated in `registerCobblemonIntegrations()` with an idempotent guard.
- The addon subscribes to `COBBLEMON_INITIALISED` and also calls the guarded registration immediately because Fabric initializes declared dependencies before dependents, so the Cobblemon event can already have emitted by the time `tc_reborn` subscribes.

### Phase 2 — Thay hook entity tổng quát bằng event Cobblemon

Status: Done on 2026-09-21.

Mục tiêu: tránh chạy logic TC trên mọi `Mob.tick` và tránh xử lý entity không phải Pokémon.

File dự kiến:

- `mixin/PokemonEntityMixin.java`
- `resources/pokemonai.mixins.json`
- `registry/BehaviorRegistry.java`
- Tạo `behaviors/BehaviorApplicator.kt`
- `spawner/hooks/CobblemonNaturalSpawnHook.kt`

Việc làm:

1. Đọc chữ ký thực tế của `POKEMON_ENTITY_LOAD` trong dependency 1.8.1 và `docs/cobblemon-1_8/08-events.md`.
2. Gắn custom goals từ event entity load thay cho mixin `Mob.tick`, nếu event chạy tại thời điểm `goalSelector` và `pokemon` đã sẵn sàng.
3. Dùng marker riêng (attachment transient hoặc entity tag ổn định) để bảo đảm goal chỉ được gắn một lần.
4. Chuyển natural herd detection từ `ServerEntityEvents.ENTITY_LOAD` sang `POKEMON_ENTITY_LOAD` hoặc `ENTITY_SPAWN` phù hợp.
5. Chỉ sau khi smoke test xác nhận event cover cả entity spawn mới và entity load từ chunk, xóa `PokemonEntityMixin` khỏi config. Nếu event thiếu một lifecycle, giữ fallback hẹp target trực tiếp `PokemonEntity`, không mixin vào `Mob`.

Tiêu chí nghiệm thu:

- Load chunk cũ: Pokémon nhận đúng goal một lần.
- Natural spawn: Pokémon nhận đúng goal một lần.
- Send-out Pokémon của người chơi không nhận wild-only behavior.
- `pokemonai.mixins.json` không còn mixin nếu public event cover đầy đủ.

Completion notes:

- Added `BehaviorApplicator` using `CobblemonEvents.POKEMON_ENTITY_LOAD` and `POKEMON_ENTITY_SPAWN`.
- Removed the broad `PokemonEntityMixin` tick injection from `Mob.tick`.
- Kept one narrow accessor mixin, `MobGoalSelectorAccessor`, only because `Mob.goalSelector` has no public getter; it does not inject or run per tick.
- Natural herd hook now listens to `POKEMON_ENTITY_SPAWN`, not Fabric `ServerEntityEvents.ENTITY_LOAD`, so chunk-loaded old Pokémon are not converted into new herds.

### Phase 3 — Hợp nhất Alpha native với vai trò thủ lĩnh TC — Hoàn thành

Mục tiêu: không còn hai định nghĩa “alpha” khác nhau.

File dự kiến:

- `spawner/hierarchy/HerdHierarchyData.kt`
- `spawner/hierarchy/HerdRole.kt`
- `spawner/GenericPackSpawner.kt`
- `behaviors/herd/*`
- `utils/DebugCommands.kt`
- `config/HerdConfigManager.kt`

Quyết định dữ liệu:

- `HerdRole.LEADER` chỉ nói vai trò trong đàn TC.
- Alpha native đọc từ `pokemon.pokemon.isAlpha` (xác nhận getter chính xác trong JAR/docs trước khi code).
- Không dùng `HerdHierarchyData.isAlpha` làm alias cho leader nữa.
- Để tương thích source, có thể deprecate getter cũ trong một phase, đổi nó thành `isLeader`, cập nhật tất cả caller rồi mới xóa.

Hành vi đề xuất:

1. Nếu một đàn native có Alpha, Alpha phải là leader TC.
2. Nếu không có Alpha, logic leader/subleader hiện tại vẫn hoạt động.
3. Alpha có thể nhận modifier cho aggression/stampede cooldown qua config, nhưng mặc định phải giữ gameplay hiện tại.
4. Không tự set `isAlpha=true` cho mọi leader; việc đó làm thay đổi stats, size, mark và TM move native.
5. Dùng `scaleModifier`/`PokemonSizeCategory` làm tie-breaker khi chọn thủ lĩnh không-alpha, thay vì coi size là alpha.
6. Thêm migration codec an toàn nếu đổi field attachment; save cũ phải load được.

Tiêu chí nghiệm thu:

- Alpha native luôn được nhận diện đúng.
- Leader thường không bị biến thành Alpha.
- Save cũ có herd attachment vẫn load.
- Debug command hiển thị riêng `Role`, `Native Alpha`, `Size Category`.

Completion notes:

- Xác nhận API public 1.8.1 trực tiếp từ dependency JAR: `Pokemon.isAlpha`, `Pokemon.scaleModifier`, `PokemonSizeCategory.fromScale(...)`.
- Xóa alias `HerdHierarchyData.isAlpha`; toàn bộ logic vai trò TC nay dùng `isLeader`.
- Natural spawn giữ nguyên Pokemon gốc làm leader, vì vậy không làm mất Alpha, size, mark, moves hoặc dữ liệu native.
- Kế vị ưu tiên native Alpha, sau đó level, `scaleModifier`, rồi UUID để kết quả ổn định.
- Không có code nào set `Pokemon.isAlpha`; leader TC thường không bị biến thành Alpha native.
- Codec attachment không đổi field, nên save cũ tiếp tục load với default hiện có.

### Phase 4 — Tích hợp herd spawn native 1.8 — Hoàn thành

Mục tiêu: giảm việc bắt một Pokémon đơn lẻ rồi `discard()` và thay bằng đàn mới.

File dự kiến:

- `spawner/GenericPackSpawner.kt`
- `spawner/PackSpawnConfig.kt`
- `spawner/hooks/CobblemonNaturalSpawnHook.kt`
- Data JSON spawn mới dưới namespace `tc_reborn` nếu API hỗ trợ datapack extension an toàn.

Việc làm:

1. Nghiên cứu `PokemonHerdSpawnDetail` trong `docs/cobblemon-1_8/01-api-04.md` và class thực tế trong JAR.
2. Prototype một species duy nhất, ưu tiên Tauros, bằng herd spawn detail/data-driven config.
3. Map native herd leader/member sang `HerdHierarchyData` sau spawn; không tạo đàn thứ hai.
4. Dùng `getHerdSize()`/`adjustHerdSize()` nếu đây là state native cần đồng bộ; không ghi đè nếu chỉ là cache nội bộ không public-stable.
5. Tôn trọng semantics 1.8 của `levelRange` và `herdLevelRange`.
6. Bảo toàn spawn bucket, rarity, condition, influence, `ENTITY_SPAWN`, cap và event của Cobblemon.
7. Sau khi Tauros ổn định mới migrate Bouffalant.
8. Giữ `GenericPackSpawner` cho debug/forced spawn nếu cần, nhưng natural spawn không được dùng cơ chế discard-and-respawn nữa.

Tiêu chí nghiệm thu:

- Không nhân đôi đàn và không loop entity load.
- Rarity thực tế không tăng so với spawn config Cobblemon.
- Alpha native dẫn đàn khi xuất hiện.
- Level của member nằm đúng range theo semantics 1.8.

Completion notes:

- Xác nhận dependency 1.8.1 đã có `pokemon-herd` spawn pool native cho Tauros và Bouffalant, gồm cả pool Alpha.
- Natural hook không còn roll chance hoặc gọi `GenericPackSpawner`; bucket, rarity, condition, influence, level range, cap và spawn event do Cobblemon giữ nguyên.
- Mapper đọc public brain memory `CobblemonMemories.HERD_LEADER` mỗi 20 tick và map leader/member native sang `HerdHierarchyData`.
- Thêm codec field tùy chọn `isNativeHerd` với default `false`; save cũ không cần migration thủ công.
- TC không chạy succession cho native herd; Cobblemon tiếp tục sở hữu việc chọn và thay leader.
- `getHerdSize()` chỉ được đọc cho debug; không gọi `adjustHerdSize()` hoặc ghi đè cache native.
- `GenericPackSpawner` được giữ lại cho debug/forced spawn TC.

### Phase 5 — Chuẩn hóa air spawning theo pipeline 1.8 — Hoàn thành

Mục tiêu: aerial spawn là phần mở rộng của BestSpawner, không phải pipeline song song.

File dự kiến:

- `flight/spawner/CustomAirSpawner.kt`
- `config/FlightConfigManager.kt`
- Có thể thêm custom `SpawningCondition`, `SpawnablePosition` hoặc `SpawnAction` dưới `flight/spawner/`.

Audit bắt buộc:

1. `dummySpawner` có được khởi tạo sau `COBBLEMON_INITIALISED` hay không.
2. `SpawningZoneInput` hiện resolve quanh player nhưng vị trí cuối lại là `targetX/targetZ`; xác minh điều kiện của detail được kiểm tra tại đúng vị trí spawn cuối.
3. Không cộng cứng `pokemonPerChunk + 5`; nếu cần reserved air capacity, đưa thành config rõ ràng hoặc dùng cap/influence chính thức.
4. Không tạo entity bằng constructor `PokemonEntity(level, pokemon)` nếu `SpawnAction`/`PokemonProperties.createEntity` đảm bảo event và metadata tốt hơn.
5. Dùng `ENTITY_SPAWN` và `SPAWN_BUCKET_CHOSEN`; không bỏ qua event cancel.
6. Dùng random source của world/server thay cho `kotlin.random.Random` để dễ test và nhất quán server.
7. Nếu custom cloud block chỉ là marker tạm, định nghĩa vòng đời/xóa block rõ ràng để không để rác world.

Hướng triển khai ưu tiên:

- Thêm loại position/condition “air” vào pipeline native nếu public API đủ ổn định.
- Nếu chưa thể, giữ adapter hiện tại nhưng selection, condition check và entity creation phải đi qua API chính thức và event đầy đủ.

Completion notes:

- `BasicSpawner` nay khởi tạo lazy sau `COBBLEMON_INITIALISED`; zone resolve quanh chính target X/Z thay vì quanh player.
- Selection giữ nguyên tổng weight của cả flying và non-flying detail; roll trúng non-flying thì không spawn, nên không normalize tăng rarity.
- Entity được tạo qua public `PokemonSpawnAction.complete()`, vì vậy influence và cancelable `ENTITY_SPAWN` được tôn trọng.
- Dùng random source của world, cap chính xác `pokemonPerChunk`, kiểm tra loaded chunk và hai block không khí trước khi spawn.
- Cloud marker có lifecycle 20 tick qua scheduled block tick hiện có; chỉ đặt sau khi native spawn thành công.

Tiêu chí nghiệm thu:

- Spawn đúng biome/time/weather/rarity/bucket.
- Event cancel ngăn được spawn.
- Không spawn vào unloaded chunk, solid block hoặc vượt cap.
- Không tăng xác suất Pokémon hiếm do lọc lại weight sai.

### Phase 6 — Evolution và moveset 1.8 — Hoàn thành

Mục tiêu: Dragon Gate tương thích event evolution và moveset builder mới.

File dự kiến:

- `evolution/EvolutionManager.kt`
- `utils/EvolutionEffectUtils.kt`
- `evolution/rules/*`
- Có thể thêm datapack `moveset_builders` nếu thật sự cần moveset riêng.

Việc làm:

1. Giữ `initializeMovesetFromDefault()` cho đổi species cưỡng bức mặc định.
2. Xác minh forced evolution có nên post/tôn trọng `EVOLUTION_ACCEPTED` và `EVOLUTION_COMPLETE`, hoặc dùng evolution controller public thay vì mô phỏng riêng.
3. Không ghi trực tiếp synced entity data (`EVOLUTION_STARTED`) nếu controller/API public cung cấp trạng thái tương đương.
4. Không tự gửi internal animation packet nếu có public evolution/animation API.
5. Nếu Dragon Gate cần moveset riêng, tạo `moveset_builder` data-driven và gọi `initializeMovesetFrom(builder)`; không hard-code move list trong Kotlin.
6. Kiểm tra Everstone/evolution lock để xác định forced evolution có chủ ý bỏ qua hay phải tôn trọng. Quyết định này phải thành config và được mô tả.

Tiêu chí nghiệm thu:

- Tiến hóa cập nhật species, form, aspects và moveset hợp lệ.
- Client thấy animation/state đúng, không desync.
- Event listener của addon khác nhận evolution nếu dùng flow native.
- Không gọi API deprecated.

Completion notes:

- Dragon Gate tìm `Evolution` native theo target species và gọi `forceEvolve()`/`evolve()`; Cobblemon sở hữu animation, species/form/aspect, moveset và event lifecycle.
- Xóa sequence tự ghi `EVOLUTION_STARTED` và tự gửi internal animation packet.
- Modifier TC và `AFTER_FORCE_EVOLVE` chỉ chạy sau `EVOLUTION_COMPLETE`.
- Thêm config `forceEvolutionIgnoresRequirements` mặc định `true` để giữ hành vi forced evolution cũ; đặt `false` để tôn trọng requirement/Everstone native.
- Không cần moveset builder riêng vì Dragon Gate dùng evolution/moveset native của Magikarp sang Gyarados.

### Phase 7 — Tận dụng size, riding, habitat và TM theo phạm vi hợp lý — Hoàn thành phạm vi an toàn

Đây là phase tùy chọn; mỗi tính năng là một PR riêng.

#### 7A. Size-aware behavior

- Dùng `scaleModifier`/`PokemonSizeCategory` để điều chỉnh khoảng cách formation, bán kính horn clash và hit avoidance.
- Clamp modifier nhỏ, ví dụ chỉ ±10–15%; không nhân trực tiếp toàn bộ tốc độ/damage theo scale.
- Không ghi đè intrinsic scale của Pokémon.

#### 7B. Riding interoperability

- Nghe `RIDE_EVENT_PRE/POST` để pause/resume custom autonomous flight khi Pokémon đang được cưỡi.
- FlightEngine không được ghi velocity hoặc `setFlying()` khi riding controller native đang sở hữu chuyển động.
- Test các rideable bay mới như Pidgeot, Crobat và Skarmory.

#### 7C. Habitat-aware living world

- Dùng habitat events/API để giảm spawn hệ thống riêng quanh Habitat Block.
- Có thể cho herd leader chọn habitat làm điểm nghỉ hoặc tuần tra, nhưng không thay đổi/cancel spawn mặc định nếu config TC chưa bật.
- Không hard-code cấu trúc habitat; đọc registry/data.

#### 7D. TM/Move Dex integration

- Event Dragon Gate có thể thưởng TM hoặc unlock liên quan, nhưng chỉ qua registry/API chính thức.
- Không sao chép dữ liệu learnset; truy vấn move/learnset registry 1.8.
- Đây là feature gameplay mới nên mặc định tắt hoặc yêu cầu user phê duyệt trước khi code.

Completion notes:

- 7A: formation spacing và horn-clash search range dùng `scaleModifier`, clamp trong `0.85..1.15`; không thay intrinsic scale, speed hoặc damage.
- 7B: nghe cả `RIDE_EVENT_PRE` và `RIDE_EVENT_POST`; FlightEngine dừng session và không ghi velocity/flying flag trong khi Pokemon có passenger.
- 7C: không can thiệp/cancel Habitat spawn; native Habitat Block tiếp tục hoạt động mặc định.
- 7D: không thêm TM reward hoặc unlock mới vì đây là thay đổi gameplay cần yêu cầu riêng.

### Phase 8 — Tests và quan sát runtime

Tạo test tối thiểu:

- Codec round-trip cho `HerdHierarchyData` cũ và mới.
- Unit test selection/role: native Alpha, leader thường, succession.
- Test weighted selection để rarity không bị normalize sai sau khi lọc flying species.
- Test state machine FlightEngine: hit, water, timeout, riding ownership.
- GameTest hoặc test server cho entity load/spawn để xác nhận goal chỉ gắn một lần.

Thêm debug có kiểm soát:

- Counter số entity được BehaviorApplicator xử lý.
- Counter natural herd/native herd/forced herd.
- Lý do air spawn bị từ chối theo category, chỉ log khi debug bật.
- Không log mỗi tick trong production.

## 6. Danh sách API cần xác minh trước khi code

AI triển khai phải tìm chữ ký chính xác trong `docs/cobblemon-1_8/` và dependency source/JAR, không đoán:

- `CobblemonEvents.COBBLEMON_INITIALISED`
- `CobblemonEvents.POKEMON_ENTITY_LOAD`
- `CobblemonEvents.ENTITY_SPAWN`
- `CobblemonEvents.SPAWN_BUCKET_CHOSEN`
- `CobblemonEvents.EVOLUTION_ACCEPTED` / `EVOLUTION_COMPLETE`
- `CobblemonEvents.RIDE_EVENT_PRE` / `RIDE_EVENT_POST`
- `PokemonHerdSpawnDetail`
- `PokemonEntity.getHerdSize()` / `adjustHerdSize(Int)`
- Getter/setter native Alpha và size (`Pokemon.isAlpha`, `scaleModifier`, `PokemonSizeCategory`)
- `MovesetBuilder` registry và `initializeMovesetFrom(...)`
- Public spawn action/create-entity path có post event và influence.

Nếu API trong docs được đánh dấu nội bộ hoặc không public-stable, không dùng chỉ vì compile được. Ghi lại phương án public thay thế hoặc giữ adapter hiện tại.

## 7. Thứ tự PR/commit đề xuất

1. `chore: establish clean Cobblemon 1.8.1 baseline`
2. `refactor: initialize Cobblemon integrations after lifecycle event`
3. `refactor: attach Pokemon behaviors through Cobblemon entity events`
4. `refactor: separate native alpha state from TC herd leadership`
5. `feat: integrate Tauros with Cobblemon 1.8 herd spawning`
6. `feat: integrate Bouffalant with native herd spawning`
7. `refactor: route aerial spawns through the 1.8 spawn pipeline`
8. `refactor: use native evolution and moveset APIs`
9. `feat: pause autonomous flight during native riding`
10. `test: add herd, flight, spawn and codec coverage`

Mỗi commit phải compile độc lập và không chứa format/rename không liên quan.

## 8. Checklist nghiệm thu cuối

- [ ] Dedicated server khởi động với Cobblemon 1.8.1.
- [ ] Client kết nối và mở Event Device bình thường.
- [ ] World/save cũ load không mất block entity, attachment hoặc config.
- [ ] Wild Pokémon, owned Pokémon và Pokémon load từ chunk được phân biệt đúng.
- [ ] Goal không bị gắn trùng.
- [x] Native Alpha và TC herd leader không bị đồng nhất sai.
- [ ] Tauros/Bouffalant herd không nhân đôi hoặc spawn loop.
- [ ] Air spawn tôn trọng bucket, rarity, cap, condition, influence và cancel event.
- [ ] Custom flight không tranh quyền điều khiển với native riding.
- [ ] Dragon Gate evolution không dùng API deprecated/internal nếu có public API.
- [ ] `compileKotlin compileJava` và test thành công bằng Java 21.
- [ ] `git diff --check` sạch; diff chỉ gồm file thuộc phase đang triển khai.
- [ ] Không chạy full `build` trừ khi chấp nhận việc copy JAR ra profile Modrinth.

## 9. Những việc không nên làm

- Không nâng lên snapshot 1.8.2/1.9 để lấy API chưa ổn định.
- Không xóa attachment TC ngay khi chuyển sang herd native; cần migration/save compatibility.
- Không đổi mọi leader thành Alpha native.
- Không gọi client class từ common/server.
- Không thao tác world/entity ngoài server thread.
- Không hard-code bucket weights hoặc tạo lại logic rarity của Cobblemon.
- Không thêm mixin vào cùng injection point với Cobblemon trước khi kiểm tra `10-mixin.md` và `99-mixin-index.md`.
- Không refactor toàn bộ package hoặc đổi registry ID trong cùng đợt migration.

## 10. Nguồn tham khảo

- Local architecture/API index: `docs/cobblemon-1_8/00-README.md`.
- Local API details: `docs/cobblemon-1_8/01-api-*.md`, `08-events.md`, `13-pokemon-*.md`, `14-spawning.md`.
- Local mixin audit: `docs/cobblemon-1_8/10-mixin.md`, `99-mixin-index.md`.
- [Cobblemon 1.8.0 official wiki changelog](https://wiki.cobblemon.com/index.php/1.8.0).
- [Cobblemon source and changelogs on GitLab](https://gitlab.com/cable-mc/cobblemon).
- [Cobblemon 1.8 milestone](https://gitlab.com/cable-mc/cobblemon/-/milestones/11).

> Ghi chú phiên bản: changelog public được tìm thấy mô tả đầy đủ 1.8.0; các khác biệt patch 1.8.1 phải được xác minh từ dependency `1.8.1+1.21.1`, local docs và source/JAR đang resolve bởi Gradle. Không suy luận API 1.8.1 từ branch `main` mới hơn.
