# Tính năng đặc biệt (Features Log) - Phiên bản V2

Danh sách các tính năng đặc biệt của các loài Pokemon AI trong quá trình phát triển (Tiếp nối V1):

- **Hệ thống Bay Đặc Thù V2 (Custom Flight System V2)**:
  - Một hệ thống bay tuỳ chỉnh (Ambient Flight) tiên tiến, độc lập hoàn toàn với AI chiến đấu của Vanilla/Cobblemon. Được thiết kế chuyên biệt để đem lại cảm giác tự nhiên và thực tế nhất khi Pokémon di chuyển quanh người chơi.
  - **Hệ Thể Lực (True Stamina System):**
    - Thay vì đếm ngược thời gian cứng nhắc, Pokémon giờ đây sở hữu một thanh thể lực thực sự (`currentStamina`).
    - Thể lực liên tục tụt giảm khi vỗ cánh bay (`FLYING`), lơ lửng (`WATER_HOVERING`), hoặc cất cánh (`TAKING_OFF`).
    - Khi hết thể lực, hệ thống sẽ cưỡng chế Pokémon rơi tự do xuống đất (`LANDING`).
    - Thể lực tự động hồi phục 1% - 10% mỗi giây khi nghỉ ngơi dưới đất. Pokémon không thể cất cánh nếu chưa hồi đủ 80% thể lực.
  - **Tương tác Mặt Nước & Trạng thái Lơ lửng:**
    - Khi đang cạn thể lực rơi xuống chạm mặt nước, thay vì bơi, Pokémon có thể dùng động lượng nảy lên (`BOUNCE`) trên mặt nước tối đa 3 lần. Mỗi lần nảy giúp bơm nhanh 33% thể lực nhưng đổi lại sẽ bị kiệt sức (giảm tốc độ hồi phục tự nhiên xuống còn 5%).
    - Khi đang ở dưới nước (hoặc chạm nước) và có lượng thể lực trên 40%, Pokémon có tỉ lệ kích hoạt chế độ lơ lửng (`WATER_HOVERING`), bay chầm chậm phía trên mặt nước 3 block và khẽ đung đưa qua lại.
  - **Cơ chế ra quyết định Độc lập (StaminaManager & FlightTransitionRules):**
    - Xóa bỏ bộ đếm thời gian cố định. Thay vào đó, bộ quy tắc chuyển trạng thái sẽ liên tục đánh giá mỗi giây. 
    - Tỉ lệ cất cánh được nội suy từ chiều cao bay ưa thích (`preferredHeight`), và kết hợp với hàm ngẫu nhiên để tạo ra sự khó đoán, giúp mỗi lần cất cánh đều mang lại cảm giác chân thực.
  - **Khả năng "Nhường Sân" cho Combat:**
    - Hệ thống tích hợp sẵn `AIFilter`. Nếu Pokémon phát hiện mục tiêu hoặc bị tấn công (Combat mode), nó sẽ lập tức ngắt hệ thống bay, hạ cánh khẩn cấp và nhường 100% quyền kiểm soát lại cho AI gốc để tham gia chiến đấu dưới mặt đất.
