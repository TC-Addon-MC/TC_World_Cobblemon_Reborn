# Hướng dẫn AI: Emotions & Attachments

## Fabric Attachment API
- Dùng Fabric AttachmentType để gắn dữ liệu tùy chỉnh vào Entity.
- Thư viện cung cấp `AttachmentRegistry`.

### Dữ liệu tạm thời (Emotion)
- Không cần lưu khi khởi động lại server.
```kotlin
val EMOTION_ATTACHMENT: AttachmentType<EmotionComponent> = 
    AttachmentRegistry.createDefaulted(
        Identifier("pokemonai", "emotion"),
        Supplier { EmotionComponent() }
    )
```

### Dữ liệu bền vững (Evolution State)
- Phải lưu lại khi restart (có `persistent()` với CODEC).
```kotlin
val EVOLUTION_STATE_ATTACHMENT: AttachmentType<EvolutionStateData> = 
    AttachmentRegistry.create(
        Identifier("pokemonai", "evo_state"),
        AttachmentRegistry.Builder<EvolutionStateData>()
            .persistent(EvolutionStateData.CODEC)
            .build()
    )
```

## Emotion Component
- Chỉ lưu trữ (Data Class), không chứa logic xử lý. Các property là biến kiểu `Int` từ 0-100.
- Cập nhật thông qua Event Handlers thay vì trực tiếp trong Goal để giữ decoupling.
- LƯU Ý: Mặc định là tạm thời (sẽ mất khi restart), nhưng CẦN cân nhắc snapshot các giá trị quan trọng (như `determination`, `rage`) vào `EvolutionStateData` mỗi khi chúng đạt mốc thay đổi đáng kể để tránh mất data quá trình của người chơi.
