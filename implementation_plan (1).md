# Refactor Hệ thống Bay Pokemon (Flight System)

Kế hoạch này giải quyết xung đột khi `CustomFlightManager` (normal/tự bay) và `DirectedFlightManager` (directed/điều khiển lệnh) cùng chạy 2 tick loop độc lập, cùng set `mob.deltaMovement` / `mob.isNoGravity` / `CobblemonBridge.setFlyingFlag` trên cùng một `PokemonEntity` mà không biết về nhau.

**Kiến trúc đích:** đảo vai trò — `FlightEngine` (đổi từ "directed") trở thành tầng DUY NHẤT chứa thuật toán vật lý/né vật cản/nước/stamina, chỉ expose `flyTo()` / `stopFlight()` / `hasActiveFlight()`. `NormalFlightAI` (đổi từ "normal") chỉ ra quyết định, gọi vào Engine, không đụng vật lý. Toàn hệ thống chỉ còn **một** tick loop, **một** map session theo UUID.

## Các Bước Triển Khai

### 1. Xóa các file cũ

```
flight/directed/DirectedFlightState.kt
flight/directed/DirectedFlightSession.kt
flight/directed/DirectedFlightBehaviors.kt
flight/directed/DirectedFlightManager.kt
flight/normal/NormalFlightBehaviors.kt
```
Sau khi xóa, nếu folder `flight/directed/` và `flight/normal/` rỗng → xóa cả folder.

### 2. Tạo `flight/engine/FlightEngine.kt` (file mới, gộp thay cho 4 file directed bị xóa)

`object FlightEngine` quản lý `activeSessions: MutableMap<UUID, FlightSession>`, có `register()` đăng ký **đúng một lần** `ServerTickEvents.END_SERVER_TICK`, tick tất cả session và remove session ở trạng thái `DONE`.

**`FlightSession`** (tách file riêng `FlightSession.kt` nếu dài): field `pokemon`, `target: Vec3`, `hover: Boolean`, `config: FlightConfig`, `ticksActive: Int`, `state: enum { FLYING, ARRIVED_HOVER, FALLING, DONE }` — chỉ 4 state nội bộ tối thiểu (không phải 5 state cũ ASCENDING/FLYING_TO_COORD/DESCENDING/HOVERING/LANDING, vì đã bỏ arc).

**API public (toàn bộ giao diện AI được phép gọi):**
```kotlin
fun flyTo(pokemon: PokemonEntity, target: Vec3, hover: Boolean = false, config: FlightConfig = FlightConfig())
fun stopFlight(pokemon: PokemonEntity)
fun hasActiveFlight(pokemon: PokemonEntity): Boolean
```

**Quy tắc overwrite session:** gọi `flyTo` cho pokemon đã có session → tạo lại session từ đầu, reset `ticksActive`, **reset `mob.deltaMovement = Vec3.ZERO` trước khi set target mới** (sửa bug thiếu reset deltaMovement của `DirectedFlightManager.restoreAndRemove()` cũ).

**Logic tick mỗi session, đúng thứ tự ưu tiên:**

1. **Filter rớt/dừng** (tập trung toàn bộ ở đây, dùng chung mọi caller):
   - `config.dropOnHit == true && mob.hurtTime > 0` → `stopFlight` nội bộ ngay (rơi tự do vật lý Minecraft bình thường), session → DONE.
   - `FlightHelpers.checkWaterStatus(mob)`:
     - `SUBMERGED` && `config.stopWhenFullySubmerged == true` → dừng bay ngay, giống dropOnHit.
     - `SURFACE` && `config.dropOnWaterSurface == true` → dừng bay ngay.
     - `SURFACE` && `config.dropOnWaterSurface == false`:
       - `config.useExtraStaminaToContinueFlight == true` → cộng bù stamina (giữ kiểu cũ `addedStamina += 200` nếu field stamina còn dùng ở tầng AI; nếu Engine không quản stamina dài hạn thì chỉ cần không dừng bay).
       - `false` → tiếp tục bay bình thường, không xử lý thêm.
   - `config.maxFlightTicks > 0 && ticksActive >= maxFlightTicks` → timeout → `stopFlight`.
2. Nếu chưa bị rớt ở bước 1:
   - Tính vector hướng hiện tại → `target` (luôn bay thẳng 3D, không arc).
   - Áp `FlightHelpers.checkObstacleAhead` (tái dùng nguyên, không viết lại thuật toán né vật cản).
   - Tính `speed` qua `resolveSpeed`/`speedPlayerScale` (copy nguyên từ `DirectedFlightBehaviors.resolveSpeed`).
   - So khoảng cách tới target với `arriveThresholdHoriz`/`arriveThresholdVert` (2 ngưỡng riêng ngang/dọc như cũ):
     - **Chưa đến** → lerp `mob.deltaMovement` theo `config.lerpFactor`, `mob.move(MoverType.SELF, ...)`, `FlightHelpers.syncRotationFromVelocity(mob)`, `FlightHelpers.applyFlyingPhysics(mob)`.
     - **Đã đến, `hover == true`** → set velocity ≈ 0 (có thể giữ sway nhẹ kiểu `tickHovering` cũ — tùy chọn), session **không bị xóa**, chờ AI gọi `flyTo` mới (overwrite theo quy tắc trên).
     - **Đã đến, `hover == false`** → chuyển state nội bộ sang rơi tự nhiên: `mob.isNoGravity = false`, không còn ép velocity, chờ `mob.onGround()` hoặc `mob.isUnderWater` → gọi `stopFlight` (trả AI, flyingFlag=false), session → DONE → bị remove ở vòng tick kế.

**Helper tái dùng nguyên trạng, không viết lại thuật toán:** `FlightHelpers.checkObstacleAhead`, `checkWaterStatus`, `applyFlyingPhysics`, `terminateFlight`, `syncRotationFromVelocity`, `nearestPlayerDistance`, `estimateGroundY`; logic `resolveSpeed` (scale theo khoảng cách player, field `speedPlayerScale`).

### 3. Sửa `FlightConfig.kt` (giữ tên file)

- Xóa: `escapeWaterOnContact`.
- Thêm:
  ```kotlin
  val dropOnHit: Boolean = false
  val stopWhenFullySubmerged: Boolean = true
  val dropOnWaterSurface: Boolean = false
  val useExtraStaminaToContinueFlight: Boolean = true
  ```
- **Giữ nguyên không động vào:** `flightSpeed`, `preferredHeight`, `maxFlightTicks`, `baseTakeoffChance`, `baseLandingChance`, `verticalSway`, `directionChangeInterval`, `takeoffAcceleration`, `landingDeceleration`, `heightMultiplier`, `activationRadius`, `lerpFactor`, `obstacleAvoidance`, `obstacleCheckRange`, `obstacleClimbAmount`, `arriveThresholdHoriz`, `arriveThresholdVert`, `speedPlayerScale`.
- **Rà soát lúc code, xóa nếu thật sự 0 reference sau khi bỏ arc:** `safeAltitudeAboveTarget`, `safeAltitudeAboveSelf` (chỉ phục vụ arc mode đã xóa), `allowWildOnly`, `overrideSpeed` — **phải grep toàn bộ project trước khi xóa**, không chỉ trong phạm vi các file đã đọc.
- `FlightConfig` là `data class` → đổi/xóa field phá vỡ mọi nơi gọi constructor. `CustomFlightInit.kt` dùng named arguments nên an toàn, nhưng vẫn phải kiểm tra lại **toàn bộ codebase** (có thể có file ngoài phạm vi đã xem) tìm mọi nơi khởi tạo `FlightConfig(...)`.

### 4. Tạo `flight/ai/NormalFlightAI.kt` (file mới, thay `flight/normal/NormalFlightBehaviors.kt`)

Vai trò thuần ra quyết định — **không một dòng nào** động vào `mob.deltaMovement`, `mob.move`, `MoverType`, hoặc check obstacle trực tiếp; mọi di chuyển đi qua `FlightEngine.flyTo`.

```kotlin
fun tickGrounded(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int)
// Giữ nguyên logic cũ: chỉ gọi FlightHelpers.restoreAI.

fun tickTakingOff(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): Boolean
// target = vị trí hiện tại + currentPreferredHeight theo Y, x/z lệch theo currentYaw.
// Gọi FlightEngine.flyTo(...) MỖI TICK (target cần cập nhật liên tục theo hướng cất cánh).
// Trả về true khi (mob.y - groundY) >= currentPreferredHeight * 0.8 (giữ điều kiện done cũ).

fun tickFlying(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int)
// Mỗi profile.config.directionChangeInterval tick: FlightHelpers.applyDirectionChange(profile) (giữ nguyên).
// Mỗi tick: target ảo = vị trí hiện tại + vector(currentYaw) * khoảng_cách_cố_định (đề xuất 10.0, giữ giống bán kính cũ trong rawVec),
//   Y = groundY + currentPreferredHeight (quyết định lúc code: có giữ hiệu ứng verticalSway hay đơn giản hóa).
// Gọi FlightEngine.flyTo(...) mỗi tick.
// KHÔNG còn trả WaterStatus — đổi return type thành Unit hoặc Boolean báo "Engine đã tự dừng bay".
// Dùng FlightEngine.hasActiveFlight(pokemon) == false sau khi gọi flyTo để biết Engine đã tự dừng (nước/bị đánh) hay chưa.

fun tickLanding(pokemon: PokemonEntity, profile: CustomFlightProfile, tick: Int): Boolean
// target = x/z hiện tại, Y = groundY.
// Gọi FlightEngine.flyTo(..., hover = false, ...) — Engine tự chuyển sang rơi tự nhiên khi gần đất.
// Trả về true khi FlightEngine.hasActiveFlight(pokemon) == false (Engine tự kết thúc khi chạm đất).
```

**Quyết định nhất quán:** vì Engine có thể tự `stopFlight` bất kỳ lúc nào (nước/hit/timeout), `NormalFlightStateMachine` phải kiểm tra `FlightEngine.hasActiveFlight(pokemon)` sau mỗi lần gọi các hàm `tickXxx` để biết khi nào tự chuyển state về `GROUNDED`/`PERCHING`. Đây thay thế hoàn toàn logic `WaterStatus` rải rác cũ.

### 5. Đổi tên + sửa `CustomFlightStateMachine.kt` → `NormalFlightStateMachine.kt`

- Đổi class name, cập nhật mọi reference (chỉ `CustomFlightManager.kt` dùng).
- Đổi `import ...flight.normal.NormalFlightBehaviors` → `import ...flight.ai.NormalFlightAI`; đổi mọi lời gọi `NormalFlightBehaviors.tickXxx` → `NormalFlightAI.tickXxx`.
- Sửa `val profile: CustomFlightProfile? = CustomFlightProfile(...)` → `val profile: CustomFlightProfile = CustomFlightProfile(...)` (bỏ nullable dư thừa). Xóa toàn bộ `profile!!`/`profile?.` thừa, đổi thành `profile.` trực tiếp — thay đổi cú pháp thuần, không đổi logic.
- Xóa toàn bộ xử lý `FlightHelpers.WaterStatus` (`when (waterResult) { SUBMERGED -> ...; SURFACE -> ...; NONE -> ... }`) trong `tickFlying()`/`tickLanding()` → thay bằng kiểm tra `FlightEngine.hasActiveFlight(pokemon)` sau khi gọi `NormalFlightAI.tickFlying(...)`; nếu `false` → `transitionTo(FlightState.GROUNDED)`.
- Giữ nguyên các phần không liên quan tới nước: `tickPerching`, đếm `spawnObserveTicks`, `minRestTicks`, `baseTakeoffChance`, `isPlayerWithinFlyRadius`, `applyInitialFlight`.
- `applyInitialFlight()` (gọi `mob.teleportTo` trực tiếp lúc spawn): **giữ nguyên**, không sửa — vì chỉ chạy 1 lần, không đụng tick loop, không xung đột Engine session.

### 6. Sửa `CustomFlightManager.kt` (giữ tên file)

- Đổi `CustomFlightStateMachine` → `NormalFlightStateMachine` ở 2 chỗ: khai báo map `mutableMapOf<UUID, NormalFlightStateMachine>()` và dòng tạo instance trong `ENTITY_LOAD`.
- Không thêm check `hasSession`/`hasActiveFlight` ở tầng Manager — vì giờ chỉ còn một tick loop vật lý (`FlightEngine`), Manager chỉ tick AI quyết định.
- Giữ nguyên cấu trúc đăng ký event (`ServerEntityEvents.ENTITY_LOAD`, `ServerTickEvents.END_SERVER_TICK`), chỉ đổi tên type.

### 7. Dọn dẹp thư mục

Xóa `flight/directed/` và `flight/normal/` sau khi rỗng.

### 8. File giữ nguyên hoàn toàn (không sửa)

```
flight/FlightState.kt
flight/FlightHelpers.kt
flight/BaseFlightSession.kt
flight/CustomFlightProfile.kt
flight/CustomFlightRegistry.kt
flight/CustomFlightInit.kt
```


## Ràng Buộc Bắt Buộc Khi Thực Thi

1. **Không đổi hằng số magic** (10.0, 0.3, 200, 0.8...) so với giá trị gốc trừ khi có lý do kỹ thuật rõ ràng — mục tiêu là refactor cấu trúc, không đổi hành vi gameplay.
2. **Mọi lời gọi `CobblemonBridge.setFlyingFlag`, `mob.isNoGravity`, `mob.deltaMovement` chỉ được nằm trong `FlightEngine.kt`** sau refactor. Nếu các dòng này xuất hiện ở `NormalFlightAI.kt` hoặc `NormalFlightStateMachine.kt` → refactor sai mục tiêu.
3. **Trước khi xóa field nào trong `FlightConfig`, phải grep toàn bộ project** (không chỉ các file đã biết) để chắc chắn không còn reference.
4. **Không sửa logic bên trong các hàm helper của `FlightHelpers.kt`**, chỉ gọi vào từ Engine/AI.
5. Sau khi code xong, biên dịch thử (`./gradlew compileKotlin` hoặc tương đương) để bắt lỗi reference do đổi tên/xóa file trước khi coi là hoàn tất.

## User Review Required

> [!WARNING]
> `applyInitialFlight()` trong State Machine vẫn dùng `mob.teleportTo` trực tiếp lúc spawn, không gọi qua Engine. Theo prompt, đây là lựa chọn **giữ nguyên** (vì không đụng tick loop/Engine session). Báo lại nếu bạn muốn đổi sang gọi Engine.

## Open Questions

Không có câu hỏi mở lớn — prompt đã chốt hướng đi rất chi tiết. Các điểm có khoảng mở (giữ sway ở `tickFlying`/`tickHovering`, field stamina cụ thể nào còn cần ở tầng AI, ngưỡng arrive dùng khoảng cách 3D hay tách ngang/dọc) sẽ quyết định cụ thể lúc đọc code thật, theo đúng hướng dẫn trong prompt, và sẽ giữ đúng hành vi gameplay gốc.

## Verification Plan

### Automated
- Chạy `./gradlew compileKotlin` (hoặc build task tương ứng) sau khi refactor để bắt lỗi reference do đổi tên/xóa file.
- Grep toàn project xác nhận không còn reference tới: `DirectedFlightManager`, `DirectedFlightSession`, `DirectedFlightBehaviors`, `DirectedFlightState`, `NormalFlightBehaviors`, `CustomFlightStateMachine`, và các field `FlightConfig` đã xóa (`escapeWaterOnContact`, và `safeAltitudeAboveTarget`/`safeAltitudeAboveSelf`/`allowWildOnly`/`overrideSpeed` nếu bị xóa).
- Grep xác nhận `mob.deltaMovement =`, `mob.isNoGravity =`, `CobblemonBridge.setFlyingFlag` chỉ xuất hiện trong `FlightEngine.kt`.

### Manual
- Spawn Pokémon, quan sát chu trình GROUNDED → TAKING_OFF → FLYING → LANDING → GROUNDED không giật/xung đột.
- Test `dropOnHit = true`: đánh mob đang bay → rơi tự do ngay.
- Test nước: `stopWhenFullySubmerged`, `dropOnWaterSurface`, `useExtraStaminaToContinueFlight` với các tổ hợp true/false, quan sát hành vi đúng spec.
- Test gọi `flyTo` liên tiếp (đổi target nhanh) → xác nhận không còn dư vận tốc cũ (bug reset deltaMovement đã sửa).
- Test `maxFlightTicks` timeout → tự rơi.
