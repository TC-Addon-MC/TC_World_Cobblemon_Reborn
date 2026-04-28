package net.minecraft.world.entity;

import net.minecraft.world.entity.ai.goal.Goal;

public class GoalInjector {
    // Vì class này nằm chung "nhà" (package) với class Mob của Minecraft,
    // nó có đặc quyền chạm vào các biến protected của Mob.
    public static void inject(Mob mob, int priority, Goal goal) {
        mob.goalSelector.addGoal(priority, goal);
    }
}