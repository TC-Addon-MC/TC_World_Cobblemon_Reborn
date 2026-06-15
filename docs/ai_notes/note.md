Mức 1: Dùng các "cổng" mà Cobblemon đã chủ động để hở
Đây không phải external Goal chạy song song nữa, mà là bơm input trực tiếp vào chính vòng tick của AI gốc — vì những hàm/field này được PokemonMoveControl.tick(), OmniPathNavigation, Brain đọc mỗi tick để quyết định hành vi. Cụ thể:

(entity.moveControl as PokemonMoveControl).startBanking(forward, upward, rightDegrees, duration) / stopBanking() — đây là fun public, gọi trực tiếp từ addon. Khi gọi, chính PokemonMoveControl.tick() (code gốc của Cobblemon) sẽ thực thi arc, không phải code của bạn.
moveControl.setWantedPosition(x, y, z, speed) — set operation = MOVE_TO, và nhánh MOVE_TO gốc (xử lý fluid/flying, xoay yRot mượt...) sẽ tự chạy.
entity.navigation.moveTo(x, y, z, speed) — pathfinding gốc của OmniPathNavigation.
entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true/false), entity.setFlying(...).
entity.brain.addActivity(activity, tasks) / brain.setActiveActivityIfPossible(activity) — chèn thêm BehaviorControl của riêng bạn vào hệ thống Activity gốc, để nó cạnh tranh/phối hợp với StayAfloatTask, FollowWalkTargetTask... đã có sẵn.

Cách này không cần Mixin, không build lại bytecode của Cobblemon, và sống sót qua các bản update tốt hơn (vì dựa vào API public, ổn định hơn implementation detail). Với 4 hành vi bạn nêu (theo dòng nước, nhảy, bay vòng cung Gyarados, tụ tập/chờ tiến hóa), tôi nghĩ mức này đã đủ cho gần như tất cả, vì startBanking, setWantedPosition, navigation.moveTo, brain.addActivity chính là những "móc" Cobblemon viết sẵn cho đúng các use-case này.


Nếu chưa chắc có thể tra: D:\Minecraft\cobblemon-main\cobblemon-main\common\src\main\kotlin