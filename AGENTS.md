# Quy tắc làm việc với TC Living World

File này áp dụng cho toàn bộ repository. Mọi AI agent phải đọc và tuân thủ trước khi sửa dự án.

## 1. Nền tảng và phiên bản mục tiêu

- Đây là addon Fabric cho Minecraft `1.21.1` và Cobblemon `1.8.1+1.21.1`.
- Dùng Java 21, Fabric Loom, Fabric API và Fabric Language Kotlin.
- Không tự ý nâng hoặc hạ Minecraft, Cobblemon, Fabric Loader, Fabric API, Loom, Kotlin hay dependency khác nếu người dùng chưa yêu cầu.
- Ưu tiên bản phát hành ổn định. Không đổi dependency sang `SNAPSHOT` nếu chưa có lý do được xác nhận.
- Giữ nguyên mod ID `tc_reborn`, namespace tài nguyên `tc_reborn` và package gốc `com.toancao.pokemonai` để không làm hỏng world/save cũ.

## 2. Phạm vi thay đổi

- Chỉ sửa phần cần thiết cho yêu cầu hiện tại. Không refactor lan rộng hoặc đổi hành vi gameplay ngoài phạm vi.
- Luôn kiểm tra `git status` và diff trước khi sửa. Các thay đổi có sẵn thuộc về người dùng; không hoàn tác, ghi đè hoặc dọn chúng.
- Không dùng `git reset --hard`, `git checkout --`, `git clean` hoặc thao tác tương đương.
- Không xóa `run/`, world, config, save, NBT, structure hoặc dữ liệu test của người dùng nếu chưa được yêu cầu rõ ràng.
- `.gradle/`, `build/`, `bin/` và `.idea/` là dữ liệu có thể tái tạo, nhưng chỉ xóa khi cần xử lý cache/build và phải báo rõ.
- Không thêm đường dẫn tuyệt đối theo máy cá nhân. Dùng đường dẫn tương đối từ project hoặc resource API.

## 3. Quy tắc Cobblemon 1.8.1

- Tra cứu `docs/cobblemon-1_8/` trước khi dùng hoặc thay đổi API Cobblemon.
- Ưu tiên API public của Cobblemon. Không dùng reflection, field private, accessor nội bộ hoặc mixin nếu đã có API public tương đương.
- Với `PokemonEntity`, ưu tiên member chính thức như `isBattling` thay vì tạo bridge/extension trùng chức năng.
- Với spawning 1.8, giữ nguyên pipeline spawn, bucket weights, influences và event của Cobblemon. Không hard-code tỉ lệ bucket nếu có thể lấy từ `Cobblemon.bestSpawner.config`.
- Khi tạo hoặc đổi Pokemon, dùng `PokemonProperties`, `PokemonSpecies`, event và API chính thức; tránh sửa trực tiếp dữ liệu đồng bộ nội bộ.
- Không gọi API client ở dedicated server. Mọi class client-only phải nằm sau client entrypoint hoặc kiểm tra environment phù hợp.
- Logic server/world/entity phải chạy trên server thread. Network handler phải chuyển công việc về main thread trước khi sửa world hoặc entity.
- Không thêm mixin mới khi event/API public giải quyết được yêu cầu. Nếu bắt buộc dùng mixin, phải giải thích injection point và kiểm tra xung đột với mixin của Cobblemon trong tài liệu.

## 4. Tính tương thích dữ liệu và cấu hình

- Mọi key registry đã phát hành phải được xem là ổn định. Không đổi ID block, item, block entity, structure, attachment hoặc packet tùy tiện.
- Thay đổi codec/NBT/config phải tương thích dữ liệu cũ hoặc có migration/default an toàn.
- Khi thêm trường config, phải cấp giá trị mặc định và giữ nguyên giá trị hiện có của người dùng.
- Không tự động ghi lại toàn bộ config chỉ để đổi format hoặc thứ tự field.
- Nếu sửa một file ngôn ngữ, phải đồng bộ key giữa tất cả file trong `assets/tc_reborn/lang/` và chạy quy trình `sync-i18n`.
- Không sửa file `.nbt` như dữ liệu text. Phải dùng công cụ NBT phù hợp và xác minh structure sau khi ghi.

## 5. Quy tắc code

- Giữ phong cách hiện có: Kotlin cho logic mới, Java chỉ khi cần bridge/tương thích Java.
- Source Kotlin và Java cùng nằm trong `src/main/java`; không tự ý di chuyển toàn bộ source tree.
- Ưu tiên code đơn giản, typed và có thể kiểm chứng. Không nuốt exception bằng `catch` rỗng.
- Không giữ reflection fallback sau khi đã có API public ổn định.
- Không thêm dependency chỉ để giải quyết việc có thể làm bằng JDK, Minecraft, Fabric hoặc Cobblemon API hiện có.
- Comment giải thích lý do hoặc ràng buộc; không lặp lại điều code đã thể hiện rõ.
- Không thay đổi định dạng hàng loạt hoặc encoding của file không liên quan.

## 6. Build và kiểm thử

- Sau khi sửa source, chạy `node debug/check-syntax.js` nếu script tồn tại. Không xem kết quả này là đủ nếu script báo đã quét `0 files`.
- Kiểm tra compile bằng Gradle với Java 21. Lệnh ưu tiên khi chỉ kiểm lỗi:

  ```powershell
  .\gradlew.bat compileKotlin compileJava --rerun-tasks --no-daemon
  ```

- Chạy test liên quan nếu có. Với thay đổi rộng, chạy:

  ```powershell
  .\gradlew.bat test --rerun-tasks --no-daemon
  ```

- Task `build` đang `finalizedBy(copyToDownload)` và sẽ copy JAR sang profile Modrinth bên ngoài repository. Chỉ chạy `build` khi người dùng yêu cầu build/package hoặc chấp nhận tác dụng phụ đó.
- Khi cần build đầy đủ:

  ```powershell
  .\gradlew.bat build --rerun-tasks --no-daemon
  ```

- Không báo hoàn thành nếu compile/build liên quan còn lỗi. Phân biệt rõ lỗi mới do thay đổi và warning/lỗi có sẵn.
- Không chạy nhiều Gradle build song song. Nếu Fabric Loom bị lock, tìm và dừng đúng daemon của project thay vì xóa cache mù quáng.

## 7. Tiêu chí hoàn thành

- Yêu cầu của người dùng hoạt động và không làm thay đổi ngoài phạm vi.
- API dùng tương thích Cobblemon 1.8.1 và Minecraft 1.21.1.
- Source liên quan compile thành công; test liên quan vượt qua.
- Resource JSON, lang, codec/NBT và client/server boundary được kiểm tra khi có thay đổi.
- Diff cuối chỉ gồm các file cần thiết; dữ liệu và thay đổi sẵn có của người dùng được bảo toàn.
- Báo ngắn gọn file đã đổi, hành vi đã thêm/sửa, lệnh xác minh và warning còn lại.
