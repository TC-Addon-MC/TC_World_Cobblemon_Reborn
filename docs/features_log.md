# Tính năng đặc biệt (Features Log)

Danh sách các tính năng đặc biệt của các loài Pokemon AI trong quá trình phát triển:

- Thêm tính năng mới: Magikarp có khả năng nhảy cao lên khỏi mặt nước. Chiều cao nhảy được tính toán ngẫu nhiên và sẽ càng cao nếu chỉ số tấn công (Attack) của nó càng lớn và trọng lượng/kích thước của nó càng nhỏ. Hành vi nhảy giờ đây đã thực tế hơn: Magikarp sẽ chủ động bơi ngoi lên bề mặt nước trước khi lấy đà nhảy vọt lên không trung.
- Thêm hệ thống xác suất hành động (Action Probability): Các hành động tự nhiên của Pokemon AI giờ đây được chia thành các mức tỷ lệ kích hoạt chuẩn hóa gồm: Siêu hiếm (Ultra Rare), Hiếm (Rare), Siêu thấp (Ultra Low), Thấp (Low), Vừa (Moderate), Trung bình (Medium), Cao (High), Siêu cao (Ultra High). Hành động nhảy của Magikarp đã được cấu hình ở mức **Thấp** (Low), với thời gian chờ (cooldown) là 30 giây để tạo cảm giác "lâu lâu mới nhảy".
- **Thử thách Vượt Long Môn (Dragon Gate Challenge)**:
  - **Sự kiện định kỳ**: Tự động kích hoạt theo chu kỳ 30 ngày trong game (ngày 30, 60, 90...). Sự kiện bắt đầu vào lúc 7h sáng (1000 ticks) và kéo dài đến qua nửa đêm.
  - **Hành trình**: Các con cá chép sẽ vượt thác, bơi từ dưới chân thác lên tới tận đỉnh. Nếu lên tới đỉnh sớm, chúng sẽ bơi lượn (hover) quanh đỉnh tháp để chờ đợi thời cơ.
  - **Tiến hóa**: Đúng 12h đêm (18000 ticks) khi mặt trăng lên cao nhất, toàn bộ cá trên đỉnh thác (đạt cấp độ 20 trở lên) sẽ lần lượt tiến hóa thành Gyarados với hiệu ứng xoáy nước và sấm sét hoành tráng. Cá không đủ cấp độ sẽ thất bại. Sau khi tiến hóa, Gyarados sẽ quay lại hành vi bơi lội hoang dã bình thường.
  - **Lệnh điều khiển**: Cung cấp lệnh `/tcpoke event dragongate start` và `/tcpoke event dragongate stop` để admin dễ dàng bắt đầu hoặc dừng khẩn cấp sự kiện để test.
- **Cấu trúc Hồ Long Môn (Magikarp Dragon Evolution Lake)**:
  - Một cấu trúc (structure) đặc biệt tự động sinh ra trong thế giới, mang kiến trúc hòn đảo/hồ nước truyền thuyết.
  - Cấu trúc tích hợp các khối đặc biệt (`TcTopBottomBlock`, `DragonGateTopBlock`, `DragonGateBottomBlock`, `DragonGateWaypointBlock`).
  - Khi cấu trúc được load, khối master sẽ dùng thuật toán tìm đường (A*) để quét các khối nước, tự động tính toán lộ trình bơi từ chân thác lên đỉnh thác, và sinh ra một đàn Magikarp thử thách ngẫu nhiên (chỉ số, giới tính, kích thước, với tỉ lệ có cá cấp cao hoặc Shiny).
