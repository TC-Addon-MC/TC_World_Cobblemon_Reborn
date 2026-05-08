package com.toancao.battlegrowth.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public interface IBattleData {
    ConcurrentHashMap<UUID, Double> getBudgetTracker();
    ConcurrentHashMap<UUID, Integer> getHpTracker();
    String getBattleDebugId();

}