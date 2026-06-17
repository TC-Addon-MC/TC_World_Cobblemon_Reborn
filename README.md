# TC Cobble World Reborn

**TC Cobble World Reborn** is an addon mod for **Cobblemon** that brings a fresh and vibrant experience to your Pokemon world through a unique AI system, mysterious structure generation, and interactive world events.

## ✨ Key Features

### 1. Realistic AI Behaviors
- **Magikarp Jump:** Magikarps can now jump out of the water instead of just swimming boringly. The jump height is dynamically calculated based on each individual's Attack stat and size/weight. 
- **Action Probability System:** Natural Pokemon AI actions are categorized into multiple rarity levels (from Ultra Rare to Ultra High). Magikarp's jumping behavior is set to a *Low* probability with a cooldown to make it feel natural and surprising.

### 2. Dragon Gate Challenge Event
Experience the legend of the carp jumping over the dragon gate right in your Minecraft world!
- **Automatic Cycle:** The event automatically triggers every 30 in-game days (Day 30, 60, 90...). It starts at 7:00 AM and lasts until midnight.
- **Waterfall Journey:** Schools of wild Magikarp will gather at the bottom of the waterfall and attempt to swim upstream to conquer the peak.
- **Evolution Moment:** At exactly midnight when the moon is at its highest peak, all Magikarps that successfully reached the top of the tower (Level 20+) will simultaneously evolve into Gyarados amidst a spectacular storm of lightning and whirlpools.

### 3. Dragon Evolution Lake Structure
- Discover a mythical architectural structure that automatically spawns in the world.
- The structure integrates smart blocks (such as `TcTopBottomBlock`, `DragonGateTopBlock`, etc.). When loaded, it utilizes the A* pathfinding algorithm to scan water blocks and create a precise swimming path for the Magikarps.
- Automatically spawns a school of Magikarps with randomized stats and genders (featuring an increased chance of spawning high-stat or Shiny Magikarps).

### 4. Event Device
- A brand new item called the **Event Device** (`tc_reborn:event_device`) helps players track the time and progress of world events.
- **Event Notice GUI:** Provides detailed information about the current event, countdowns for start times, and phase endings.
- **Crafting Recipe:** Moon Stone in the center, 2 Magnets on the sides, Clock on top, surrounded by Red Apricorns.
- **Event Notice API:** A robust API that allows other mod developers to easily integrate their own events into the display system.

## 🛠 Admin Commands
To facilitate testing and recording, the mod provides the following commands:
- `/tcpoke event dragongate start` - Immediately starts the Dragon Gate event.
- `/tcpoke event dragongate stop` - Immediately stops the ongoing event.

---
*Requires [Cobblemon](https://cobblemon.com/) and Fabric API to work.*
*Developed by Toan Cao.*
