import java.io.File

val replacements = mapOf(
    "net.minecraft.util.math.BlockPos" to "net.minecraft.core.BlockPos",
    "net.minecraft.entity.Entity" to "net.minecraft.world.entity.Entity",
    "net.minecraft.world.World" to "net.minecraft.world.level.Level",
    "net.minecraft.server.world.ServerWorld" to "net.minecraft.server.level.ServerLevel",
    "ServerWorld" to "ServerLevel",
    "net.minecraft.entity.EntityType" to "net.minecraft.world.entity.EntityType",
    "net.minecraft.entity.mob.PathAwareEntity" to "net.minecraft.world.entity.PathfinderMob",
    "PathAwareEntity" to "PathfinderMob",
    "net.minecraft.nbt.NbtCompound" to "net.minecraft.nbt.CompoundTag",
    "NbtCompound" to "CompoundTag",
    "writeCustomDataToNbt" to "saveWithoutId",
    "readCustomDataFromNbt" to "load",
    "net.minecraft.util.math.Vec3d" to "net.minecraft.world.phys.Vec3",
    "Vec3d" to "Vec3",
    "net.minecraft.fluid.Fluids" to "net.minecraft.world.level.material.Fluids",
    "net.minecraft.fluid.FlowableFluid" to "net.minecraft.world.level.material.FlowingFluid",
    "FlowableFluid" to "FlowingFluid",
    "net.minecraft.particle.DustParticleEffect" to "net.minecraft.core.particles.DustParticleOptions",
    "DustParticleEffect" to "DustParticleOptions",
    "net.minecraft.server.network.ServerPlayerEntity" to "net.minecraft.server.level.ServerPlayer",
    "ServerPlayerEntity" to "ServerPlayer",
    "net.minecraft.text.Text" to "net.minecraft.network.chat.Component",
    "Text.literal" to "Component.literal",
    "Text" to "Component",
    "world.isDay" to "level.isDay()",
    "world.isNight" to "level.isNight()",
    "world.isRaining" to "level.isRaining()",
    "world.getBiome" to "level.getBiome",
    "fluid.isOf" to "fluid.is",
    "fluid.get" to "fluid.getValue",
    "world.isWater" to "level.getFluidState(pos).is(Fluids.WATER)",
    "entity.world" to "entity.level()",
    "entity.blockPos" to "entity.blockPosition()",
    "entity.velocity" to "entity.deltaMovement",
    "entity.velocityModified" to "entity.hasImpulse",
    "entity.boundingBox" to "entity.boundingBox",
    "world.getOtherEntities" to "level.getEntitiesOfClass",
    "entity.squaredDistanceTo" to "entity.distanceToSqr",
    "world.spawnParticles" to "level.sendParticles",
    "player.sendMessage" to "player.sendSystemMessage",
    "Math.sqrt" to "kotlin.math.sqrt",
    "Vector3f" to "org.joml.Vector3f"
)

File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
    var content = file.readText()
    for ((k, v) in replacements) {
        content = content.replace(k, v)
    }
    file.writeText(content)
}

File("src/test/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
    var content = file.readText()
    for ((k, v) in replacements) {
        content = content.replace(k, v)
    }
    file.writeText(content)
}
