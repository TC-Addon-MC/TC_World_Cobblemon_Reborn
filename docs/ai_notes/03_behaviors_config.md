# Hướng dẫn AI: Behaviors & Config

## Config File
- Config chỉ lưu trữ thông tin (behaviors list, priorities, base emotion).
- KHÔNG chứa logic `tick()` hay hàm của entity.

```kotlin
object MagikarpConfig {
    val species = "magikarp"
    val behaviors = listOf(
        BehaviorRegistry.Entry(priority = 3, factory = ::JumpOutOfWaterGoal)
    )
}
```

## Goal Class
- Thiết kế Goal theo `Goal` của Minecraft.
- `canStart()`: Trả về điều kiện bắt đầu.
- `tick()`: Xử lý logic mỗi tick.
- KHÔNG gọi trực tiếp hàm xử lý Tiến Hoá từ trong Goal. Chỉ gọi fire Event (ví dụ: `DragonGatePassedEvent`) để một event handler khác tiếp nhận và cập nhật emotion / state.

## Utilities (Tái sử dụng code)
- Mọi logic phức tạp (tính vector, tìm điểm nước, check cooldown) phải được tách ra file Utils (`WaterUtils`, `MovementUtils`, `RandomUtils`).
- LƯU Ý: Minecraft không có block "dòng nước mạnh" (strong current) tự nhiên. Trong `WaterUtils.isNearStrongCurrent()`, cần định nghĩa rõ logic này: ví dụ kiểm tra hướng chảy của nước (fluid flow direction) hoặc kiểm tra block nước đang rơi từ trên cao xuống (waterfall).
- Điều này giúp tái sử dụng cho các loài khác như Goldeen, Feebas.
