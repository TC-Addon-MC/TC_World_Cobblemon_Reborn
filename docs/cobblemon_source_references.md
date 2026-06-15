# Danh Sách Các File Quan Trọng Trong Source Code Cobblemon Gốc

Tài liệu này lưu lại các đường dẫn tới các file quan trọng trong mã nguồn gốc của Cobblemon (`D:\Minecraft\cobblemon-main\cobblemon-main\common\src\main\kotlin\com\cobblemon\mod\common`) mà chúng ta đã tra cứu trong quá trình debug hệ thống tiến hóa. Nó sẽ giúp bạn tra cứu lại nhanh chóng chức năng của từng class mà không cần phải grep/tìm kiếm toàn bộ project.

---

### 1. Xử lý Logic Tiến Hóa (Server-side)
**Đường dẫn:** `\pokemon\Evolution.kt`
- **Công dụng:** Chứa hàm `forceEvolve(pokemon)`. Đây là nơi Cobblemon gốc bắt đầu chuỗi tiến hóa.
- **Chi tiết quan trọng:** 
  - Khóa Entity bằng cách set `EVOLUTION_STARTED` thành true.
  - Sau 1 giây (1F), gửi `PlayPosableAnimationPacket` với nội dung `"q.bedrock_stateful('evolution', 'evolution', 'endures_primary_animations');"`.

### 2. Định Nghĩa Thực Thể Pokémon
**Đường dẫn:** `\entity\pokemon\PokemonEntity.kt`
- **Công dụng:** Class trung tâm quản lý Entity của Pokémon trên server và client.
- **Chi tiết quan trọng:** 
  - Chứa cờ `EVOLUTION_STARTED` (được đồng bộ qua mạng bằng `EntityDataAccessor`).
  - Hàm check `isEvolving` sẽ return true nếu cờ này bật, từ đó chặn các AI Goal (như đi lại, tấn công, bị bắt) khi đang tiến hóa.

### 3. Gói Tin Chạy Animation (Packet)
**Đường dẫn:** `\net\messages\client\animation\PlayPosableAnimationPacket.kt`
- **Công dụng:** Định nghĩa cấu trúc của gói tin gửi từ Server xuống Client để ép Client chơi một animation bất kỳ.
- **Chi tiết quan trọng:** Tham số animation truyền vào là một `Set<String>` (thường dùng `Collections.singleton()` trong Java).

### 4. Xử Lý Mạng & Lambda Lọc Người Chơi
**Đường dẫn:** `\api\net\NetworkPacket.kt`
- **Công dụng:** Interface gốc cho tất cả các packet của Cobblemon.
- **Chi tiết quan trọng:** 
  - Chứa hàm `sendToPlayersAround(..., exclusionCondition: (ServerPlayer) -> Boolean = { false })`.
  - Đây là nguyên nhân của mọi bug NPE và tàng hình: `exclusionCondition` dùng để **LOẠI TRỪ** người chơi khỏi danh sách nhận packet nếu trả về `true`. Nó không cho phép truyền `null`. Nếu gọi từ Java, phải truyền một `Function1` trả về `false`.

### 5. Nhận Packet & Áp Dụng Animation (Client-side)
**Đường dẫn:** `\client\net\animation\PlayPosableAnimationHandler.kt`
- **Công dụng:** Packet Handler chạy trên máy Client khi nhận được `PlayPosableAnimationPacket`.
- **Chi tiết quan trọng:** Tìm `PosableEntity` tương ứng trên Client và gọi `delegate.addFirstAnimation(...)`.

### 6. Quản Lý Animation Bedrock & MoLang
**Đường dẫn:** `\client\render\models\blockbench\PosableModel.kt`
- **Công dụng:** Parse các chuỗi dạng hàm như `q.bedrock_stateful(...)` (được gọi là MoLang expression) và trích xuất animation ra từ kho dữ liệu Bedrock.
- **Chi tiết quan trọng:** Nó dịch chuỗi `q.bedrock_stateful('evolution', 'evolution', ...)` thành việc tìm animation có tên `animation.evolution.evolution` trong file `evolution.animation.json` của Resource Pack/Mod.

### 7. Render Pokémon & Xử lý Beam (Client-side)
**Đường dẫn:** `\client\render\pokemon\PokemonRenderer.kt`
- **Công dụng:** Xử lý việc vẽ model, particle và các hiệu ứng trực quan của Pokémon.
- **Chi tiết quan trọng:** 
  - Chứa logic của `beamMode`. Nếu `beamMode == 1` (send-out), Renderer sẽ đè lên mọi logic vẽ thông thường để thực hiện hiệu ứng scale model từ 0 (vừa ném Pokeball). Việc gọi `setBeamMode(1)` khi tiến hóa sẽ làm sập quá trình render animation tiến hóa Bedrock gốc.

### 8. Định Vị Điểm Nối Particle (Locator)
**Đường dẫn:** `\client\render\models\blockbench\LocatorAccess.kt`
- **Công dụng:** Quản lý các "xương" (bone/locator) ảo để gắn particle vào model.
- **Chi tiết quan trọng:** Tự động tiêm (inject) một locator tên là `"middle"` vào trọng tâm của mọi Pokémon nếu file `.geo` không định nghĩa sẵn. Đây là lý do tại sao particle tiến hóa (`locator: "middle"`) không bao giờ bị lỗi mất tọa độ dù model đó không hề vẽ cục bone nào tên là middle.

### 9. Quản Lý Di Chuyển Chuyên Sâu (AI Vật Lý)
**Đường dẫn:** `\entity\pokemon\ai\PokemonMoveControl.kt`
- **Công dụng:** Hệ thống tiếp nhận tọa độ đích và tính toán góc quay, lực đẩy, quán tính nước/không khí, và thao tác di chuyển mượt mà của Pokémon. 
- **Chi tiết quan trọng:** 
  - Kế thừa từ `MoveControl` của Minecraft gốc nhưng có tùy chỉnh sâu cho Pokémon.
  - Khi gọi hàm `moveControl.setWantedPosition(...)`, hệ thống sẽ tự dùng hàm nội suy `Mth.approachDegrees` để quay đầu cá về mục tiêu (tránh bẻ góc giật cục) và tự giới hạn `speed` phù hợp với môi trường lỏng. Tránh dùng `deltaMovement` thủ công nếu muốn di chuyển tự nhiên.

### 10. Giao Diện Pokedex (Client-side)
**Đường dẫn:** `\client\gui\pokedex\PokedexGUI.kt`
- **Công dụng:** Class điều khiển chính của màn hình Pokedex GUI.
- **Chi tiết quan trọng:** Quản lý render các thành phần của Pokedex (khung nền, các icon tab, chức năng kéo thả chuột, lọc dữ liệu). Khung nền gốc của nó được load từ `textures/gui/pokedex/pokedex_screen.png`.

### 11. Hằng Số UI Pokedex
**Đường dẫn:** `\client\gui\pokedex\PokedexGUIConstants.kt`
- **Công dụng:** Chứa các tỉ lệ kích thước gốc của màn hình Pokedex.
- **Chi tiết quan trọng:** Thiết lập `BASE_WIDTH = 345` và `BASE_HEIGHT = 207`. Mọi khung hình UI nên được tính toán và Scale từ kích thước này để vừa vặn với độ phân giải màn hình của người chơi.

### 12. Utilities Vẽ UI Mở Rộng
**Đường dẫn:** `\api\gui\GuiUtils.kt`
- **Công dụng:** Cung cấp các hàm vẽ GUI nâng cao.
- **Chi tiết quan trọng:** Chứa hàm `blitk`, thay thế cho `GuiGraphics.blit` mặc định để vẽ các texture với thông số blend, scale, offset linh hoạt. (Lưu ý: Dễ dính lỗi Intermediary Mapping với `PoseStack` khi dùng làm thư viện trên Fabric Kotlin).

### 13. Hỗ Trợ Render Text Tự Động Co Giãn
**Đường dẫn:** `\client\render\RenderHelper.kt`
- **Công dụng:** Các hàm helper tiện ích phục vụ rendering.
- **Chi tiết quan trọng:** Chứa hàm `drawScaledText`, có chức năng tự động tính toán font size và ép chữ nhỏ lại (scale down) nếu chiều dài của đoạn Text vượt quá giới hạn khung `maxCharacterWidth`.
