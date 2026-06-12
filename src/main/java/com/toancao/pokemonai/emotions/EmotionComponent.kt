package com.toancao.pokemonai.emotions

class EmotionComponent(
    rage: Int = 0,
    determination: Int = 0,
    fear: Int = 0,
    protectiveness: Int = 0,
    curiosity: Int = 0
) {
    var rage: Int = rage
        set(value) { field = value.coerceIn(0, 100) }
    
    var determination: Int = determination
        set(value) { field = value.coerceIn(0, 100) }
        
    var fear: Int = fear
        set(value) { field = value.coerceIn(0, 100) }
        
    var protectiveness: Int = protectiveness
        set(value) { field = value.coerceIn(0, 100) }
        
    var curiosity: Int = curiosity
        set(value) { field = value.coerceIn(0, 100) }

    init {
        this.rage = rage
        this.determination = determination
        this.fear = fear
        this.protectiveness = protectiveness
        this.curiosity = curiosity
    }
}



