# TC Living World

## Mục tiêu cố định

- Fabric; Minecraft `1.21.1`; Cobblemon `1.8.1+1.21.1`; Java 21; Kotlin.
- Mod ID/namespace: `tc_reborn`; package: `com.toancao.pokemonai`.
- Cobblemon: API/event public; server thread; client/server tách biệt.
- Source Kotlin/Java cùng trong `src/main/java`.

## Công cụ & lệnh

- Xem thay đổi: `git status --short`
- Kiểm tra syntax: `node debug/check-syntax.js`
- Compile source: `.\gradlew.bat compileKotlin compileJava`
- Chạy test: `.\gradlew.bat test`
- Test một lớp: `.\gradlew.bat test --tests "package.ClassName"`
- Theo dõi test: `.\gradlew.bat test --continuous`
- Test sạch: `.\gradlew.bat test --rerun-tasks --no-daemon`
- Chạy client: `.\gradlew.bat runClient`
- Build package: `.\gradlew.bat build`

## Đường dẫn quan trọng

- Tổng quan dự án: `README.md`
- Source chính: `src/main/java/`
- Resource mod: `src/main/resources/`
- Test source: `src/test/`
- Kiến trúc AI: `docs/ai_notes/`
- Cobblemon API: `docs/cobblemon-1_8/00-README.md`
- Spawn API: `docs/cobblemon-1_8/14-spawning.md`
- Mixin index: `docs/cobblemon-1_8/99-mixin-index.md`
- Thiết kế hệ thống: `docs/plans/`

## Quy tắc dự án

- Đọc tài liệu liên quan trước khi sửa.
- Giữ registry/packet ID và dữ liệu cũ tương thích.
- Config mới có default; giữ giá trị người dùng.
- Lang đồng bộ toàn bộ `assets/tc_reborn/lang/`; chạy `sync-i18n`.
- NBT dùng công cụ NBT; xác minh sau ghi.
- Spawn giữ pipeline/weights/influences/events; dùng `Cobblemon.bestSpawner.config`.
- Network handler chuyển sửa world/entity về main thread.
- Mixin mới: tra index, giải thích injection point.

## Quy tắc UI/UX

HUD/menu — giữ phong cách Minecraft/Cobblemon hiện có — pixel typography — layout gọn — tương phản rõ — input phản hồi tức thì — không giảm readability hoặc immersion.

## Tuyệt đối không làm

- Không ghi đè thay đổi có sẵn.
- Không sửa file ngoài phạm vi.
- Không đổi dependency khi chưa yêu cầu.
- Không đổi mod ID, namespace hoặc package.
- Không dùng reflection khi có API public.
- Không thêm mixin khi event đáp ứng.
- Không hardcode giá trị đã có config.
- Không thêm dependency chỉ để tiện.
- Không thêm comment `//` mới.
- Không sửa `.nbt` như text.
- Không chạy Gradle song song.
- Không bỏ qua lỗi compiler hoặc test.
- Không chạy `build` nếu chưa yêu cầu.
- Không xóa world, save hoặc config.
- Không dùng `reset --hard`, `checkout --`, `clean`.

## Cobblemon mới

- Tham khảo đầy đủ: `src/main/java/com/toancao/pokemonai/`.
- Bắt buộc: species config, config model/default, config manager/load, behavior/goal, registry và init.
- Có spawn/pack/evolution/network/resource/lang thì đăng ký đủ pipeline tương ứng.
- Đối chiếu implementation tương tự; không để placeholder hoặc phần chưa nối.
- Xác minh config được load, behavior được register và code compile.

`build` gọi `copyToDownload`, copy JAR ra profile Modrinth.
