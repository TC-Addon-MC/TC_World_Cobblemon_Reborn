package com.toancao.pokemonai.flight.engine

/** Runtime owner, không persist NBT. Battle/riding là ưu tiên cao hơn cả hai owner này. */
enum class FlightControlOwner {
    AUTONOMOUS,
    DIRECTED
}
