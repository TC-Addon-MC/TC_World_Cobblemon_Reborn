# Hướng dẫn AI: Mixins & Hệ thống Goal

## Hook vào PokemonEntity
- Không thể gọi trực tiếp `entity.goalSelector.add(...)` từ bên ngoài một cách sạch sẽ.
- Sử dụng **Mixin** vào `PokemonEntity.initGoals()` để chèn Goal cho wild Pokémon.

```kotlin
@Mixin(PokemonEntity::class)
abstract class PokemonEntityMixin : PathAwareEntity(null, null) {
    @Inject(method = ["initGoals"], at = [At("TAIL")])
    fun onInitGoals(ci: CallbackInfo) {
        val species = (this as PokemonEntity).pokemon.species.name.lowercase()
        BehaviorRegistry.getGoals(species).forEach { (priority, goalFactory) ->
            goalSelector.add(priority, goalFactory(this as PokemonEntity))
        }
    }
}
```

## Lưu ý quan trọng
- Mixin phải được khai báo trong file `fabric.mod.json` hoặc `mixins.json`.
- Ép kiểu `this` thành `PokemonEntity` vì ta đang mixin vào class đó, và nó kế thừa `PathAwareEntity`.
