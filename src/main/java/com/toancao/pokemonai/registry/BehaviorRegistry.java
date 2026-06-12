package com.toancao.pokemonai.registry;

import com.toancao.pokemonai.behaviors.PokemonBehavior;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BehaviorRegistry {
    public static final BehaviorRegistry INSTANCE = new BehaviorRegistry();

    public static class Entry {
        private final int priority;
        private final PokemonBehavior factory;

        public Entry(int priority, PokemonBehavior factory) {
            this.priority = priority;
            this.factory = factory;
        }

        public int getPriority() { return priority; }
        public PokemonBehavior getFactory() { return factory; }
    }

    private static final Map<String, List<Entry>> speciesBehaviors = new HashMap<>();

    public static void register(String species, List<Entry> behaviors) {
        speciesBehaviors.computeIfAbsent(species.toLowerCase(), k -> new ArrayList<>()).addAll(behaviors);
    }

    public static List<Entry> getGoals(String species) {
        return speciesBehaviors.getOrDefault(species.toLowerCase(), new ArrayList<>());
    }

    public static void clear() {
        speciesBehaviors.clear();
    }
}
