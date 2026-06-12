# Hướng dẫn AI: Hệ thống Tiến hoá (Evolution)

## Evolution Manager
- Hệ thống lắng nghe `ServerTickEvents.END_WORLD_TICK`.
- Lặp qua các wild PokemonEntity (cứ mỗi 20 ticks / 1 giây). Điều kiện check wild: `entity.pokemon.ownerUUID == null && entity.pokemon.storeCoordinates.get() == null`.
- Kiểm tra các `EvolutionRule` có trong `EvolutionRegistry` cho loài đó.

## Evolution Rule
- Interface nhận vào `PokemonEntity`, `EmotionComponent`, `EvolutionStateData`.
- Trả về đối tượng `EvolutionResult` (chứa target species) hoặc `null` nếu chưa đủ điều kiện.

```kotlin
data class EvolutionResult(
    val targetSpecies: String,
    val isRageBurst: Boolean = false  // dùng để báo hiệu event đặc biệt, set NBT
)

interface EvolutionRule {
    fun check(entity: PokemonEntity, emotion: EmotionComponent, state: EvolutionStateData): EvolutionResult?
}
```

## Kích hoạt Tiến hoá qua Cobblemon
- Dùng API chính thức của Cobblemon.
- Không tự ý kill entity rồi spawn con mới.

```kotlin
val evolution = entity.pokemon.species.evolutions.find { it.result.name == result.targetSpecies }
if (evolution != null) {
    entity.pokemon.evolutionProxy.server().runEvolution(evolution)
}
```
- LƯU Ý QUAN TRỌNG: Hàm `find` có thể trả về `null` nếu loài targetSpecies chưa được khai báo dạng tiến hóa này trong datapack của Cobblemon. Nếu dùng custom evolution ngoài hệ thống file datapack mặc định, bạn phải **register evolution vào species trước**, hoặc tự implement logic xóa entity cũ & spawn entity mới.

## Lấy Species
- KHÔNG dùng `PokemonSpecies.MAGIKARP`.
- ĐÚNG: `PokemonSpecies.getByName("magikarp")`.
