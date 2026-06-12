# Tổng quan Kiến trúc (Architecture Overview)

File này tóm tắt công dụng của toàn bộ các file trong source code để bạn dễ dàng nắm bắt cấu trúc project.

## 1. Core & Entry Point
*   **`PokemonAIMod.kt`**: File khởi chạy chính của Mod (ModInitializer). Nhiệm vụ của file này là đăng ký tất cả các hệ thống (Attachments, Event Handlers, Tick Handlers) và load các Config (như MagikarpConfig) vào Registry khi Minecraft khởi động.

## 2. Hệ thống Attachments (Lưu trữ dữ liệu lên Entity)
Nằm trong thư mục `attachment/`:
*   **`PokemonAttachments.kt`**: Đăng ký các `AttachmentType` thông qua Fabric API. Khai báo 2 vùng nhớ: 1 cho Cảm xúc (Emotion) và 1 cho Trạng thái Tiến hóa (Evolution State).
*   **`EvolutionStateData.kt`**: Data class chứa các flag vĩnh viễn (như `dragonGatePassed`). Dữ liệu này được lưu lại vào ổ cứng (NBT) nên khi tắt server bật lại vẫn còn.

## 3. Hệ thống Cảm Xúc (Emotions)
Nằm trong thư mục `emotions/`:
*   **`EmotionComponent.kt`**: Data class lưu trữ các điểm số cảm xúc hiện tại của Pokémon (rage, determination, fear, v.v.). Giá trị từ 0-100. Dữ liệu này chỉ tồn tại tạm thời (bay mất khi restart server).
*   **`EmotionType.kt`**: Định nghĩa Enum cho các loại cảm xúc.
*   **`EmotionEventHandler.kt`**: Lắng nghe các sự kiện (Events) xảy ra trong game (ví dụ: bị người chơi đánh, vượt thác thành công) để cộng/trừ điểm cảm xúc vào `EmotionComponent`.

## 4. Hệ thống Hành Vi (Goals & Behaviors)
Nằm trong thư mục `registry/` và `behaviors/`:
*   **`BehaviorRegistry.kt`**: Kho chứa danh sách các tập tính (Goal) của từng loài Pokémon. Khi một con Pokémon xuất hiện, nó sẽ nhìn vào đây để xem mình cần làm hành động gì.
*   **`PokemonBehavior.kt`**: Định nghĩa Typealias cho hàm tạo Goal (`(PokemonEntity) -> Goal`).
*   **Các class trong `behaviors/water/`**:
    *   `JumpOutOfWaterGoal.kt`: Xử lý logic nhảy lên mặt nước.
    *   `SwimUpwardGoal.kt`: Xử lý logic bơi lên cao.
    *   `DragonGateChallengeGoal.kt`: Xử lý logic bơi ngược dòng nước mạnh (thử thách Long Môn). Nếu thành công/thất bại sẽ gửi tín hiệu cho `EmotionEventHandler`.

## 5. Hệ thống Tiến Hóa Đặc Biệt (Evolution)
Nằm trong thư mục `evolution/`:
*   **`EvolutionManager.kt`**: Hệ thống chạy ngầm. Cứ mỗi 1 giây (20 ticks), nó sẽ quét tất cả Pokémon hoang dã quanh người chơi và kiểm tra xem có con nào đủ điều kiện tiến hóa không.
*   **`EvolutionRule.kt`**: Interface chuẩn mực. Bất kỳ luật tiến hóa nào cũng phải implement hàm `check()` trả về loài tiến hóa thành (ví dụ: Gyarados) hoặc `null` nếu chưa đủ điều kiện.
*   **`EvolutionRegistry.kt`**: Kho chứa các luật tiến hóa (Rule) cho từng loài.
*   **`rules/MagikarpEvolutionRules.kt`**: Chứa logic tiến hóa thực tế của Magikarp (Tiến hóa do Determination + Vượt Long Môn, hoặc Tiến hóa do Rage quá mức).

## 6. Mixin (Can thiệp vào Core của Cobblemon)
Nằm trong thư mục `mixin/`:
*   **`PokemonEntityMixin.kt`**: Dùng để "tiêm" (inject) mã nguồn vào class `PokemonEntity` của Cobblemon. Cụ thể là tiêm vào hàm `initGoals()` để nhét các Goal từ `BehaviorRegistry` vào bộ não của Pokémon hoang dã.

## 7. Configuration & Utils
*   **`pokemon/MagikarpConfig.kt`**: File cấu hình tập trung. Đóng vai trò liên kết loài "magikarp" với các Goal (`JumpOutOfWaterGoal`, ...) và cài đặt các chỉ số cảm xúc cơ bản.
*   **Thư mục `utils/`**: Chứa các file hỗ trợ tính toán dùng chung để tránh viết lặp code:
    *   `WaterUtils.kt`: Tìm điểm nước, kiểm tra dòng nước mạnh.
    *   `MovementUtils.kt`: Xử lý các phép toán tác dụng lực (Velocity) để entity di chuyển/nhảy.
    *   `RandomUtils.kt`: Hỗ trợ quay random xác suất (ví dụ 5% thành công).
