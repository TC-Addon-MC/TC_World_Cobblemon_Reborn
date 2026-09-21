# Kế hoạch triển khai nâng cấp hệ thống bay — phạm vi khả thi cao

## 1. Mục tiêu của tài liệu

Tài liệu này là chỉ dẫn thực thi dành cho AI/coding agent tiếp theo. Mục tiêu là nâng cấp hệ thống bay của Pokémon theo các hướng có xác suất thành công cao trên:

- Minecraft `1.21.1`.
- Cobblemon `1.8.1+1.21.1`.
- Fabric, Java 21 và Kotlin hiện có của dự án.

Không được hiểu tài liệu này là yêu cầu viết lại toàn bộ hệ thống trong một lượt. Mỗi phase có cổng kiểm chứng riêng. Nếu một cổng thất bại, phải dừng phase đó, ghi lại kết quả và không tiếp tục mở rộng dựa trên giả định chưa được chứng minh.

## 2. Kết quả mong muốn

Sau khi hoàn tất các phase đã vượt qua kiểm chứng:

1. Hệ thống không kéo Pokémon xuống đáy biển khi lơ lửng trên mặt nước.
2. Pokémon chỉ đáp xuống vị trí chứa vừa bounding box, không phải nước/lava và không ép loài không biết đi đáp đất.
3. Không còn vòng lặp `LANDING -> BOUNCE -> TAKING_OFF` cộng độ cao vô hạn.
4. Bay tự nhiên, bay có chỉ định, battle và riding không đồng thời ghi movement lên cùng một Pokémon.
5. Machine/session được tạo và dọn theo lifecycle entity; scan AABB chỉ còn là fallback có tần suất thấp.
6. Bay tự nhiên ưu tiên API movement public của Cobblemon nếu proof-of-concept xác nhận đạt yêu cầu runtime.
7. Không thêm mixin vào `PokemonEntity.travel`, `tick`, `moveControl` hoặc navigation trong phạm vi kế hoạch này.
8. Config V3 hiện tại và world/save cũ tiếp tục đọc được.

## 3. Sự thật kỹ thuật đã xác minh

### 3.1 API Cobblemon public có thể sử dụng

Đã đối chiếu source Cobblemon 1.8.1 trong `docs/cobblemon-1_8/` và source dependency:

- `PokemonEntity.canFly()`.
- `PokemonEntity.isFlying()`.
- `PokemonEntity.setFlying(Boolean)`.
- `PokemonEntity.navigation`, implementation là `OmniPathNavigation`.
- `OmniPathNavigation.moveTo(...)` và pathfinding không trung qua `OmniPathNodeMaker`.
- `PokemonMoveControl.startBanking(...)`.
- `PokemonMoveControl.stopBanking()`.
- `CobblemonEvents.POKEMON_ENTITY_LOAD`.
- `CobblemonEvents.POKEMON_ENTITY_SPAWN`.
- `CobblemonEvents.RIDE_EVENT_PRE/POST`.
- Fabric `ServerEntityEvents.ENTITY_UNLOAD`.

Không dùng reflection để truy cập các API trên.

### 3.2 Giới hạn phải chấp nhận

Source Cobblemon tự mô tả `OmniPathNavigation` là chưa hoàn hảo. Vì vậy:

- Không hứa pathfinding hoàn hảo trong tán rừng dày hoặc không gian cực hẹp.
- Không gọi `navigation.moveTo` với mục tiêu mới mỗi tick; việc đó gây path recalculation tốn CPU và dễ rung.
- Không tự viết A* 3D mới trong phase này.
- Không dùng vài ray đơn giản rồi quảng cáo là obstacle avoidance hoàn chỉnh.

### 3.3 Lỗi kiến trúc hiện tại cần xử lý

Hiện tại `FlightEngine.tickFlying()` vừa ghi `deltaMovement`, vừa gọi `mob.move(MoverType.SELF, ...)` trong callback cuối server tick. Đồng thời Cobblemon vẫn chạy Brain, navigation, `PokemonMoveControl` và `PokemonEntity.travel()` trong entity tick.

`FlightHelpers.disableAI()` chỉ gọi `navigation.stop()`, không vô hiệu hóa Brain hoặc move control. Do đó custom engine không thực sự sở hữu độc quyền movement.

Ngoài ra, API directed flight và autonomous flight đang dùng chung `FlightEngine.flyTo()`. `NormalFlightAI` có thể cập nhật lại target mỗi tick và ghi đè lệnh `PokemonAI.flyTo(...)` từ addon khác.

## 4. Phạm vi được phép thay đổi

### File dự kiến sửa

- `src/main/java/com/toancao/pokemonai/flight/FlightHelpers.kt`
- `src/main/java/com/toancao/pokemonai/flight/FlightConfig.kt` — chỉ khi thật sự cần field có default an toàn
- `src/main/java/com/toancao/pokemonai/flight/CustomFlightManager.kt`
- `src/main/java/com/toancao/pokemonai/flight/NormalFlightStateMachine.kt`
- `src/main/java/com/toancao/pokemonai/flight/ai/NormalFlightAI.kt`
- `src/main/java/com/toancao/pokemonai/flight/engine/FlightEngine.kt`
- `src/main/java/com/toancao/pokemonai/flight/engine/FlightSession.kt`
- `src/main/java/com/toancao/pokemonai/api/PokemonAI.kt` — chỉ để giữ semantics API directed flight
- `src/main/java/com/toancao/pokemonai/utils/DebugCommands.kt` — chỉ nếu cần debug kiểm chứng

### File có thể thêm

- `flight/navigation/WaterSurfaceResolver.kt`
- `flight/navigation/LandingSiteFinder.kt`
- `flight/navigation/NativeFlightMovement.kt`
- Test tương ứng trong `src/test/` nếu hạ tầng test hiện tại hỗ trợ Minecraft classes.

Không bắt buộc tạo cả ba file. Nếu mỗi helper ngắn và chỉ có một caller, giữ trong file hiện có để tránh over-engineering.

### Ngoài phạm vi

- Không thêm animation/model/texture mới.
- Không tạo roll/pitch hiển thị custom trên model.
- Không viết lại hệ thống herd hoặc Brain của Cobblemon.
- Không thêm `FlockFollowGoal` mới.
- Không persist toàn bộ state machine vào NBT.
- Không đổi mod ID, namespace hoặc registry ID.
- Không thay dependency/version.
- Không sửa gameplay stamina/chance hàng loạt để che lỗi movement.
- Không bỏ `CloudBlock`; block này đã tự xóa sau 20 tick trong `CloudBlock.kt`.

## 5. Quy tắc bắt buộc cho AI triển khai

1. Chạy `git status --short` và đọc diff trước khi sửa.
2. Working tree đang có thay đổi của người dùng; không revert, reset hoặc format lại các file không liên quan.
3. Tra cứu `docs/cobblemon-1_8/` và source Cobblemon trước khi gọi API.
4. Không giữ hai implementation movement cùng hoạt động “để fallback”. Tại một thời điểm chỉ một owner được điều khiển Pokémon.
5. Không gọi `level.getChunk(...)` để ép load chunk khi tìm mục tiêu/điểm đáp. Chỉ dùng chunk đã load.
6. Không log mỗi tick khi debug tắt.
7. Không tự ghi lại toàn bộ config chỉ để thêm field nếu không cần thiết.
8. Mỗi phase phải compile và test runtime trước khi sang phase sau.

---

## Phase 0 — Baseline và proof-of-concept native movement

### Mục tiêu

Xác nhận movement public của Cobblemon có đáp ứng cảm giác bay tối thiểu trước khi thay engine hiện tại.

### 0.1 Ghi baseline

Trước khi sửa:

1. Bật debug hiện có.
2. Chọn đúng một loài thử nghiệm phổ biến, biết bay và biết đi, ví dụ Pidgeot.
3. Ghi lại video hoặc log ngắn cho các tình huống:
   - Cất cánh trên đồng trống.
   - Bay qua tường rộng 3–5 block.
   - Bay gần cây.
   - Hạ cánh cạnh nước.
   - Bắt đầu battle khi đang bay.
   - Mount/dismount nếu loài hỗ trợ riding.
4. Không cân chỉnh preset trước khi có baseline.

### 0.2 Tạo proof-of-concept tối thiểu

Không xóa engine cũ ngay. Tạo một đường thử chỉ áp dụng cho loài test hoặc debug command:

1. Kiểm tra `pokemon.canFly()`; nếu false thì từ chối.
2. Gọi `pokemon.setFlying(true)`.
3. Gọi navigation tới một target cố định cách 8–16 block, target nằm trong chunk đã load.
4. Không ghi `deltaMovement`, `isNoGravity` hoặc gọi `mob.move()` trong đường thử.
5. Không thay target sớm hơn 20 tick, trừ khi target hoàn tất/không thể tới.
6. Thử `PokemonMoveControl.startBanking()` cho một vòng lượn ngắn; luôn gọi `stopBanking()` khi kết thúc, battle, riding hoặc unload.

### 0.3 Cổng quyết định

Chỉ chuyển autonomous flight sang native movement nếu tất cả điều sau đúng:

- Pokémon thực sự bay lên mục tiêu trên không.
- Không rơi vì gravity trong khi path đang chạy.
- Không xuyên block trong bài test tường cơ bản.
- Khi navigation dừng, không để move control ở trạng thái banking vô hạn.
- Battle/riding có thể giành quyền điều khiển lại ngay.
- Không có exception dedicated server.

Nếu proof-of-concept thất bại:

- Không thêm mixin để ép nó thành công.
- Giữ engine hiện tại và chỉ thực hiện Phase 1, 2, 3, 5 theo hướng sửa lỗi an toàn.
- Ghi rõ API nào thất bại và hành vi runtime quan sát được.

---

## Phase 1 — Sửa xác định mặt nước

### Vấn đề

`FlightHelpers.estimateWaterSurfaceY()` đang dùng:

```kotlin
Heightmap.Types.OCEAN_FLOOR
```

`OCEAN_FLOOR` trả về nền rắn dưới cột nước, không phải mặt nước. Cộng `2.0` có thể tạo target nằm dưới nước sâu.

### Thiết kế

Tạo hàm nullable, ví dụ:

```kotlin
fun findWaterSurfaceY(level: Level, x: Int, startY: Int, z: Int): Double?
```

Yêu cầu:

1. Chỉ dùng cho cột nước đang liên quan tới entity; không quét toàn chiều cao world mỗi tick.
2. Bắt đầu từ `floor(entity.boundingBox.minY)` hoặc block Y gần entity.
3. Nếu block bắt đầu không chứa nước, tìm nước trong khoảng nhỏ hợp lý quanh entity, ví dụ `-2..2`.
4. Khi tìm thấy nước, đi lên đến block cuối cùng còn `FluidTags.WATER`.
5. Tính mặt fluid bằng:

```kotlin
pos.y + fluidState.getHeight(level, pos)
```

6. Dừng ở `level.maxBuildHeight`.
7. Không coi lava là nước.
8. Trả `null` nếu không có cột nước liên tục.

### Cập nhật caller

Trong `NormalFlightAI.tickWaterHovering()`:

- Nếu không resolve được mặt nước, thoát `WATER_HOVERING` theo transition an toàn hoặc chuyển sang flight bình thường.
- Target Y là `surfaceY + hoverOffset`.
- Không dùng `OCEAN_FLOOR` làm fallback.
- Trước khi gửi target mới, kiểm tra vị trí target và bounding box không nằm trong fluid/collision.

### Kiểm chứng

- Hồ sâu 2 block.
- Biển sâu trên 20 block.
- Sông chảy.
- Waterlogged block.
- Mặt nước có lily pad.
- Pokémon không bị kéo về đáy.

---

## Phase 2 — Chọn điểm đáp an toàn

### 2.1 Điều kiện trước khi đáp

Không ép đáp đất nếu:

- `pokemon.canWalk()` là false.
- Pokémon đang battle, sleeping, busy hoặc đang được cưỡi.
- Species movement configuration tránh đất (`avoidsLand`) và API public xác minh được.
- Không tìm được điểm đáp an toàn trong chunk đã load.

Với Pokémon không thể đáp, chuyển sang hover an toàn hoặc tiếp tục bay và thử lại sau; không thả rơi tự do chỉ vì stamina bằng 0.

### 2.2 `LandingSiteFinder`

API gợi ý:

```kotlin
fun findLandingSite(
    pokemon: PokemonEntity,
    forwardDistance: Int = 12,
    searchRadius: Int = 6
): Vec3?
```

Thuật toán khả thi cao:

1. Tạo danh sách candidate theo hướng nhìn hiện tại và các vòng xung quanh.
2. Chỉ xét X/Z thuộc chunk đã load (`hasChunkAt`/API tương đương không ép load).
3. Dùng `MOTION_BLOCKING_NO_LEAVES` để có ground candidate ban đầu nếu mapping 1.21.1 xác nhận enum tồn tại.
4. Ground block phải:
   - Có collision shape không rỗng.
   - Không chứa water/lava fluid.
   - Không thuộc leaves nếu chưa hỗ trợ perch trên lá.
   - Có mặt trên đủ ổn định cho entity.
5. Khoảng trống phía trên phải chứa toàn bộ bounding box của Pokémon:
   - Dịch bounding box hiện tại tới candidate.
   - Dùng `level.noCollision(entity, movedBox)` hoặc API tương đương.
   - Kiểm tra fluid trong toàn bộ box, không chỉ một block ở tâm.
6. Candidate không vượt world border/build height.
7. Chấm điểm:
   - Ưu tiên phía trước thay vì quay ngược đột ngột.
   - Ưu tiên chênh lệch độ cao nhỏ.
   - Ưu tiên khoảng cách vừa phải.
   - Phạt candidate gần nước/lava nếu cần.
8. Trả candidate tốt nhất; không sửa world.

### 2.3 Hành vi khi không có điểm đáp

- Không gọi lại `land()` mỗi tick.
- Đặt cooldown tìm lại, ví dụ 20–40 tick.
- Giữ target hover hoặc bay vòng ngắn trong chunk đã load.
- Có timeout tổng để tránh session vĩnh viễn, nhưng timeout phải trả quyền về autonomous flight chứ không teleport/rơi cứng.

### 2.4 Xóa bounce tăng độ cao vô hạn

Trong `NormalFlightStateMachine.tickLanding()`:

- Xóa hành vi `currentPreferredHeight += 10.0` sau mỗi bounce.
- `bounceCount` chỉ là giới hạn retry, không thay đổi vĩnh viễn preferred height.
- Sau tối đa số retry hiện có, chọn một trong hai:
  - Điểm đáp hợp lệ khác.
  - Hover và retry sau cooldown.
- Không cố đáp xuống nước chỉ vì đã retry đủ ba lần, trừ species/config thực sự cho phép.

### Kiểm chứng

- Đồng cỏ phẳng.
- Rừng lá dày.
- Mái nhà.
- Bờ hồ.
- Đại dương không có đất gần.
- Pokémon rất lớn.
- Loài bay nhưng không biết đi.
- Chunk cạnh candidate chưa load.

---

## Phase 3 — Quyền sở hữu movement

### Mục tiêu

Một Pokémon chỉ có đúng một nguồn ra lệnh movement tại một thời điểm.

### 3.1 Mô hình owner tối thiểu

Thêm enum runtime, không persist NBT:

```kotlin
enum class FlightControlOwner {
    AUTONOMOUS,
    DIRECTED
}
```

Battle và riding là trạng thái ưu tiên cao hơn owner runtime, không nhất thiết là enum member nếu code đơn giản hơn.

### 3.2 Tách API command

Không để `NormalFlightAI` và public `PokemonAI.flyTo()` cùng gọi một method không phân biệt nguồn.

Thiết kế gợi ý:

- `FlightEngine.flyAutonomouslyTo(...)` — chỉ package/internal.
- `FlightEngine.flyTo(...)` — giữ public semantics directed hiện tại.
- `FlightSession.owner` ghi nguồn command.

Quy tắc:

1. Directed command có ưu tiên hơn autonomous.
2. Khi session là `DIRECTED`, `NormalFlightStateMachine` vẫn được phép cập nhật stamina/debug nhưng không được đổi target hoặc movement state.
3. Khi directed session kết thúc, machine quay lại state hợp lý dựa trên vị trí hiện tại, không tự nhận là grounded nếu vẫn ở trên không.
4. Riding, battle, sleeping, busy hoặc combat target phải:
   - Dừng navigation/banking custom.
   - Xóa session tương ứng.
   - Trả gravity/flying flag theo semantics native phù hợp.
   - Không ghi movement trong cùng tick sau khi nhả quyền.
5. `RIDE_EVENT_PRE/POST` chỉ là tín hiệu bổ sung; kiểm tra `pokemon.isVehicle` vẫn phải tồn tại để chống miss event.

### 3.3 Event semantics

- `FLIGHT_START` chỉ fire khi một session mới thật sự được chấp nhận.
- `FLIGHT_END` chỉ fire một lần cho mỗi session kết thúc.
- Cập nhật target của cùng session không fire start/end lặp lại.
- Session bị unload phải được dọn mà không phát movement lên entity đã unload.

### Kiểm chứng

1. Gọi public `PokemonAI.flyTo()` trong lúc autonomous flight đang chạy.
2. Xác nhận target directed không bị `NormalFlightAI` ghi đè ở tick kế tiếp.
3. Bắt đầu battle giữa đường.
4. Mount giữa đường.
5. Dismount và chờ autonomous resume.
6. Unload chunk khi session đang hoạt động.

---

## Phase 4 — Chuyển autonomous movement sang API native (chỉ khi Phase 0 đạt)

### 4.1 Tạo adapter nhỏ

`NativeFlightMovement` chỉ bọc API public cần thiết:

```kotlin
object NativeFlightMovement {
    fun moveTo(pokemon: PokemonEntity, target: Vec3, speed: Double): Boolean
    fun startBanking(pokemon: PokemonEntity, ...): Boolean
    fun stop(pokemon: PokemonEntity, clearFlyingWhenSafe: Boolean)
}
```

Không tạo abstraction lớn hơn nếu không cần.

### 4.2 `moveTo`

1. Từ chối nếu entity không sống, removed, client-side hoặc `!canFly()`.
2. `setFlying(true)` trước khi yêu cầu path trên không.
3. Gọi `navigation.moveTo(...)` bằng speed phù hợp.
4. Không gọi lại mỗi tick:
   - Lần đầu state.
   - Khi target đổi đáng kể.
   - Khi navigation không progress nhưng chưa tới target, sau cooldown.
   - Tối đa khoảng một lần mỗi 20 tick cho roaming thông thường.
5. Theo dõi tiến triển bằng khoảng cách tới target và `navigation.isInProgress`.
6. Nếu stuck nhiều lần, chọn target mới hoặc chuyển state; không spam recompute path.

### 4.3 Lượn vòng

Ưu tiên `PokemonMoveControl.startBanking()` vì Cobblemon đã dùng nó trong `CircleAroundTask` và `FollowHerdLeaderTask`.

- Bắt đầu banking với duration ngắn và refresh có kiểm soát.
- Luôn stop banking khi rời circular state.
- Không đồng thời gửi navigation target mới mỗi tick trong lúc banking.
- Không quảng cáo banking là model roll; đây là chuyển động vòng/yaw native.

### 4.4 Hover

Hover không được teleport hoặc snap cứng vào target.

- Dùng target ổn định, chỉ refresh nếu drift vượt threshold.
- Nếu navigation native không giữ hover ổn định trong proof runtime, cho phép một correction velocity nhỏ và hiếm, nhưng không gọi `mob.move()` thủ công.
- Correction phải nằm trong một helper duy nhất, clamp rõ ràng và không chạy khi battle/riding.

### 4.5 Loại bỏ movement custom khỏi autonomous path

Sau khi migration chạy đúng:

- Autonomous flight không gọi `mob.move(MoverType.SELF, ...)`.
- Autonomous flight không ghi `deltaMovement` mỗi tick.
- Autonomous flight không bật `isNoGravity` liên tục.
- Không giữ engine cũ chạy song song như fallback cho cùng entity.

Directed flight có thể được migrate sang native navigation trong cùng phase nếu các test API directed vượt qua. Nếu chưa đạt, giữ directed engine riêng nhưng ownership ở Phase 3 phải ngăn mọi xung đột.

### 4.6 Không sửa native Brain/herd trong phase này

Cobblemon đã có `FollowHerdLeaderTask`, bao gồm follower bay theo leader và bắt chước banking. Tuy nhiên task có phụ thuộc species behaviour, activity và memories. Không tự nhét task vào Brain runtime trong kế hoạch này.

Nếu cần bay đàn sau này, tạo kế hoạch riêng dựa trên data-driven behaviour config hoặc native herd spawn, không viết thêm Goal song song.

---

## Phase 5 — Lifecycle và hiệu năng

### 5.1 Tạo machine theo event

Trong `CustomFlightManager.register()`:

- Subscribe `POKEMON_ENTITY_LOAD`.
- Subscribe `POKEMON_ENTITY_SPAWN` nếu cần cho đường spawn không phát load event.
- Dùng một hàm idempotent, ví dụ `tryAttach(entity)`.

`tryAttach` phải:

1. Chỉ chạy server side.
2. Chỉ nhận entity sống, chưa removed.
3. Chỉ nhận species có config trong `CustomFlightRegistry`.
4. Tôn trọng wild-only semantics hiện tại qua `AIFilter`; không vô tình thêm autonomous flight cho Pokémon của người chơi.
5. Không tạo machine thứ hai nếu UUID đã tồn tại.
6. Chọn initial state theo môi trường thực tế:
   - Trên không và biết bay: airborne state.
   - Chạm đất/nước: `PERCHING` hoặc `GROUNDED` phù hợp.
7. Không ghi tag persistent chỉ để đánh dấu machine runtime.

### 5.2 Dọn theo unload

Dùng `ServerEntityEvents.ENTITY_UNLOAD`:

- Nếu entity là `PokemonEntity`, remove machine/session theo UUID.
- Dọn debug display.
- Stop banking/navigation state do mod sở hữu.
- Không teleport, spawn particle hoặc sửa block trong callback unload.
- Không giữ strong reference tới level/entity sau remove.

### 5.3 Scan fallback

Không xóa scan ngay trong commit đầu tiên.

1. Tăng interval fallback hợp lý, ví dụ ít nhất vài giây.
2. Scan chỉ để bắt entity bị miss event.
3. Không scan riêng 128 block từ từng machine để tìm player.
4. Dùng player list của `ServerLevel` hoặc cache nearest player theo world/tick.
5. Không tick machine thuộc entity/chunk không còn active.

### 5.4 Player context

Thay `level.getEntitiesOfClass(Player, AABB...)` trên mỗi machine bằng một trong hai cách đơn giản:

- Duyệt `level.players()` mỗi 20 tick và tìm khoảng cách nhỏ nhất; hoặc
- Manager tính context tập trung cho các machine.

Không xây spatial index riêng trừ khi profiler chứng minh cần.

### Kiểm chứng hiệu năng

- 1 người chơi, 20 Pokémon bay.
- 4 người chơi ở cùng dimension.
- 2 dimension có người chơi.
- Teleport xa làm unload nhiều entity.
- Xác nhận map machine/session trở về kích thước hợp lý sau unload.
- Không log mỗi entity mỗi tick.

---

## Phase 6 — Air spawner: chỉ sửa phần chắc chắn

Giữ pipeline Cobblemon 1.8.1 đang dùng trong `CustomAirSpawner`:

- `BasicSpawner`.
- Bucket từ `Cobblemon.bestSpawner.config.worldBuckets`.
- Influences.
- `PokemonSpawnAction.complete()`.
- Held items, drops và level range native.
- Cap `pokemonPerChunk` chính xác.

Chỉ thực hiện các chỉnh sửa an toàn:

1. Sau spawn, machine phải được attach idempotently; không phụ thuộc scan sau đó.
2. Không đặt entity vào block chưa load.
3. Kiểm tra khoảng trống theo bounding box, không chỉ hai block air nếu Pokémon lớn.
4. `CloudBlock` đã tự xóa sau 20 tick; không thêm hệ thống cleanup thứ hai.
5. Không persist cloud/anchor chỉ để khôi phục hiệu ứng spawn.
6. Nếu entity spawn trên không nhưng attach machine thất bại, phải khôi phục gravity hoặc loại bỏ cloud marker an toàn; không để entity no-gravity vĩnh viễn.
7. Không normalize lại rarity sau khi lọc flying species.

Không thay spawn rate/preset trong phase kỹ thuật này.

---

## Phase 7 — Config và tương thích dữ liệu

Ưu tiên không thêm field config cho các sửa lỗi ở trên.

Nếu bắt buộc thêm field:

1. Field phải có default trong `FlightConfig`.
2. Gson phải đọc config V3 cũ khi field không tồn tại.
3. Không đổi tên/xóa field cũ trong cùng thay đổi.
4. Không tăng version chỉ vì thêm field nullable/default-compatible; chỉ tăng khi cấu trúc thực sự thay đổi.
5. Không ghi đè giá trị preset tùy chỉnh của người dùng.
6. Viết kiểm tra migration V1/V2/V3 nếu thay loader.

Không thêm enum `flapper/soarer/hover` trong đợt sửa nền tảng. Các semantics hiện tại (`hoverOnly`, `canGroundHover`, `circularFlightChance`) đủ để ổn định movement trước.

---

## Phase 8 — Debug và quan sát runtime

Debug chỉ bật qua hệ thống debug hiện có.

Thông tin hữu ích:

- State machine state.
- Control owner: autonomous/directed/none.
- Navigation progress.
- Target hiện tại.
- Stamina.
- Landing retry/cooldown.
- Lý do nhả quyền: battle, riding, sleeping, busy, unload, dead.

Không tạo `TextDisplay` khi debug tắt. Khi unload hoặc machine remove, display phải bị discard.

Có thể bổ sung debug command để:

- Chỉ định native target.
- Buộc tìm điểm đáp và in candidate được chọn.
- In water surface Y tại vị trí Pokémon.
- In số machine/session theo dimension.

Không đưa debug-only state vào NBT.

---

## 9. Test matrix bắt buộc

### 9.1 Movement cơ bản

- `GROUNDED -> TAKING_OFF -> FLYING`.
- `FLYING -> CIRCULAR_FLYING -> FLYING`.
- `FLYING -> LANDING -> GROUNDED`.
- Hover-only không trôi vô hạn.
- Không bay ngang/lùi kéo dài sau khi đổi target.

### 9.2 Môi trường

- Đồng trống.
- Rừng.
- Hang có trần thấp.
- Mái nhà.
- Hồ nông.
- Biển sâu.
- Waterlogged blocks.
- World border.
- Gần min/max build height.

### 9.3 Kích thước Pokémon

- Loài nhỏ.
- Loài trung bình.
- Loài có bounding box lớn.
- Candidate landing phải bị từ chối nếu chỉ đủ chỗ cho loài nhỏ.

### 9.4 Quyền điều khiển

- Autonomous đang chạy, gọi directed `flyTo`.
- Directed kết thúc, autonomous resume.
- Battle bắt đầu giữa flight.
- Pokémon nhận combat target.
- Pokémon ngủ/busy.
- Mount và dismount.
- Recall/capture giữa flight.
- Death/remove/unload giữa flight.

### 9.5 Lifecycle

- Spawn tự nhiên.
- Air spawner.
- Entity load lại từ chunk.
- Out/in world.
- Teleport dimension.
- Server restart.
- Không duplicate machine/session.

### 9.6 Dedicated server

- Không load client class.
- Không gọi render/client API từ server path.
- Client khác nhìn thấy flying flag/rotation hợp lý.

---

## 10. Trình tự commit khuyến nghị

Không gộp tất cả vào một commit khó debug.

1. `fix(flight): resolve real water surface height`
2. `fix(flight): select safe landing sites`
3. `fix(flight): enforce directed and autonomous ownership`
4. `refactor(flight): use native Cobblemon movement for autonomous flight` — chỉ sau Gate 0
5. `perf(flight): attach and detach machines from entity lifecycle`
6. `test(flight): cover water landing ownership and unload`

Mỗi commit phải compile độc lập.

## 11. Lệnh kiểm tra

Sau mỗi phase có source change:

```powershell
node debug/check-syntax.js
```

Nếu script báo quét `0 files`, không xem là đã kiểm tra thành công.

Compile bắt buộc:

```powershell
.\gradlew.bat compileKotlin compileJava --rerun-tasks --no-daemon
```

Sau thay đổi rộng:

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
```

Không chạy `build` trừ khi người dùng yêu cầu package, vì task hiện có side effect copy JAR ra profile bên ngoài repository.

## 12. Tiêu chí hoàn thành cuối

Chỉ báo hoàn thành khi:

- Compile Kotlin và Java thành công với Java 21.
- Test liên quan vượt qua.
- Pokémon không bị kéo xuống đáy khi water-hover.
- Landing không chọn nước/lava hoặc không gian không vừa bounding box.
- Directed target không bị autonomous AI ghi đè.
- Battle/riding giành quyền movement ngay và không còn session ghi velocity phía sau.
- Unload xóa machine/session, không leak UUID/entity/display.
- Không có mixin movement mới.
- Config V3 cũ vẫn load được.
- Diff cuối không ghi đè thay đổi có sẵn của người dùng.

## 13. Nội dung phải báo lại cho người dùng

AI triển khai phải báo ngắn gọn:

1. Phase nào đã thực hiện và phase nào bị dừng ở gate.
2. File đã đổi.
3. Movement hiện dùng native navigation hay custom directed engine.
4. Kết quả test theo môi trường chính.
5. Lệnh compile/test đã chạy.
6. Giới hạn còn lại, đặc biệt là pathfinding trong rừng/hang và animation model.
