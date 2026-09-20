# Cobblemon: TC Living World

**Cobblemon: TC Living World** is a Fabric addon that makes wild Pokemon feel like part of a living Minecraft ecosystem. It expands Cobblemon with natural AI behaviors, herd organization, physical flight, aerial spawning, emotions, and world events.

## Features

### Flight and aerial spawning

- Physics-based takeoff, directional flight, gliding, hovering, and landing.
- Stamina-aware behavior that makes Pokemon rest or find safe ground.
- Mid-air spawning for suitable flying species.
- Specialized hovering behavior for psychic and magnetic Pokemon.
- Optional compatibility with Fight or Flight Reborn.

### Herd behavior

- Leaders, sub-leaders, adults, and juveniles within wild herds.
- Formation following, shared aggression, leadership succession, and stampedes.
- Species-specific behavior for Pokemon such as Tauros and Bouffalant.

### World events

- A recurring Dragon Gate challenge for wild Magikarp.
- Magikarp jumping behavior influenced by individual attributes.
- A craftable Event Device for viewing event phases and countdowns.

## Requirements

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.8.x

## Commands

- `/tcpoke event dragongate start` starts the Dragon Gate event.
- `/tcpoke event dragongate stop` stops the current Dragon Gate event.
- `/tcpoke debug on|off` toggles AI and stamina diagnostics.
- `/tcpoke action all @e[distance=..10] circle` directs nearby flying Pokemon into circular flight.

## Configuration

Flight settings are stored in `config/config_pokeflight.json`. Species can be assigned to flight tiers without modifying the mod. Existing user values are preserved when missing defaults are added.

## Developer API

The public API is available under `com.toancao.pokemonai.api`.

```kotlin
PokemonAI.flyTo(pokemon, target, hover = false)
val stamina = PokemonAI.getRemainingStamina(pokemon)
PokemonAI.spawnCloudPokemon(serverPlayer, "pidgeot")
```

`PokemonAIEvents` exposes hooks for flight, Dragon Gate events, Magikarp jumps, aerial spawning, forced evolution, and custom AI filtering.

## Compatibility policy

The internal mod ID remains `tc_reborn` so existing worlds retain registered blocks, items, structures, and saved data after the public project rename.

## Credits

- Developer: Toan Cao
- Built for the Cobblemon ecosystem.
