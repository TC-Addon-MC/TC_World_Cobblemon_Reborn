<div align="center">
  <h1>🐉 TC Cobble World Reborn</h1>
  <p><b>A next-generation addon for Cobblemon that brings your Pokémon world to life!</b></p>
</div>

---

**TC Cobble World Reborn** is a comprehensive enhancement mod for **Cobblemon** (Fabric). It completely overhauls the AI behaviors of Pokémon—introducing a highly realistic, physics-based flight engine, dynamic mid-air spawning, magical world events like the Dragon Gate, and an expansive API for developers.

## 📑 Table of Contents
- [✨ Features](#-features)
  - [Flight & Spawning](#flight--spawning)
  - [World Events & Behaviors](#world-events--behaviors)
- [📦 Installation](#-installation)
- [🚀 Usage & Commands](#-usage--commands)
- [⚙️ Configuration](#️-configuration)
- [🛠️ Developer API](#️-developer-api)
- [❤️ Credits](#️-credits)

---

## ✨ Features

### Flight & Spawning
- **Dynamic Physics Flight Engine:** Flying Pokémon now perform smooth take-offs, directional flying with momentum, circular gliding, and calculated soft landings.
- **Stamina System:** Flight requires stamina! Once depleted, Pokémon will intelligently search for land or hover above the water to rest.
- **Mid-Air Cloud Spawning:** Bypassing traditional ground-spawn limits, flying Pokémon dynamically spawn high in the sky on temporary `CloudBlocks` that naturally dissipate. 
- **Hover Mechanics:** Psychic and Magnetic Pokémon gracefully hover 3-4 blocks above the terrain instead of perching awkwardly on the ground.
- **Fight or Flight Compatibility:** Seamlessly integrates with the *Fight or Flight Reborn* mod. Pokémon gracefully yield flight controls back to combat AI when engaged.

### World Events & Behaviors
- **Dragon Gate Challenge:** Every 30 in-game days, wild Magikarps gather at the Dragon Gate waterfall. Those who conquer the peak and reach level 20+ will magically evolve into Gyarados at midnight!
- **Magikarp Jump AI:** Magikarps dynamically leap out of the water, with jump heights scaling by their individual Attack stat and size.
- **Event Device:** A custom craftable item (`tc_reborn:event_device`) that opens a beautiful GUI tracking the countdowns and phases of world events.

---

## 📦 Installation

1. Install **[Fabric Loader](https://fabricmc.net/)** (for Minecraft 1.21.1).
2. Install the **[Fabric API](https://modrinth.com/mod/fabric-api)**.
3. Install **[Cobblemon](https://cobblemon.com/)**.
4. Download the latest release of **TC Cobble World Reborn** and drop it into your `mods/` folder.

*(Note: This mod is fully compatible with **Fight or Flight Reborn**!)*

---

## 🚀 Usage & Commands

To facilitate server administration and testing, the mod provides several in-game commands:

- `/tcpoke event dragongate start` : Instantly triggers the Dragon Gate event.
- `/tcpoke event dragongate stop` : Forcefully stops an ongoing event.
- `/tcpoke debug on/off` : Toggles real-time AI states and stamina text displays above Pokémon.
- `/tcpoke action all @e[distance=..10] circle` : Forces nearby flying Pokémon into their circular gliding pattern.

---

## ⚙️ Configuration

The mod features a highly optimized JSON config system (`config/config_pokeflight.json`).

- **9 Specialized Tiers:** Flight stats (speed, stamina, hover chance, circular flight radius) are grouped into 9 balanced presets ranging from *Small Birds* to *Giant Dragons*.
- **Easy Customization:** To add flight abilities to a newly added Pokémon, simply assign its ID to a Tier number (e.g., `"cobblemon:ho_oh": "6"`). The mod injects the AI automatically.
- **Non-destructive:** Updates to the mod will safely merge missing fields without overwriting your custom values.

---

## 🛠️ Developer API

**TC Cobble World Reborn** exposes a robust API for other modders to hook into and control our custom systems!

### 1. The `PokemonAI` Object
Use `com.toancao.pokemonai.api.PokemonAI` to directly control AI behaviors:
```kotlin
// Force a Pokemon to fly to a specific coordinate
PokemonAI.flyTo(pokemon, targetVec3, hover = false)

// Check remaining stamina
val stamina = PokemonAI.getRemainingStamina(pokemon)

// Spawn a Pokemon directly on a cloud above the player
PokemonAI.spawnCloudPokemon(serverPlayer, "pidgeot")
```

### 2. Event Hooks (`PokemonAIEvents`)
Listen to events via `com.toancao.pokemonai.api.PokemonAIEvents` using Fabric's `EventFactory`:

- **Flight Events:** `FLIGHT_START` (cancellable) & `FLIGHT_END`
- **Dragon Gate Events:** `DRAGON_GATE_START` (cancellable), `DRAGON_GATE_END`, & `ON_MAGIKARP_JUMP` (modify jump velocity!)
- **Air Spawning:** `ON_AIR_SPAWN` (dynamically change the spawned species or cancel the spawn).
- **AI Filters:** `ON_AI_FILTER_CHECK` (pause our custom AI when your mod's item interacts with the Pokémon).

---

## ❤️ Credits
- **Developer:** Toan Cao
- **Dependencies:** Built on the incredible [Cobblemon](https://cobblemon.com/) architecture.
