package com.toancao.battlegrowth.mixin;

import com.cobblemon.mod.common.net.messages.client.battle.BattleUpdateTeamPokemonPacket;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.experience.ExperienceSource;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownInterpreter;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.toancao.battlegrowth.LevelOverrideTracker;
import com.toancao.battlegrowth.util.IBattleData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.toancao.battlegrowth.ExpConfig;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(ShowdownInterpreter.class)
public class MoveActionResponseMixin {

    static {
        System.out.println("BattleGrowth mod đã khởi động");
    }

    private static final Pattern MOVE_PATTERN   = Pattern.compile("^\\|move\\|((p[12][a-z]):[^|]*)\\|([^|]+)\\|");
    private static final Pattern DAMAGE_PATTERN =
            Pattern.compile("^\\|[-]?damage\\|((p[12][a-z]):[^|]*)\\|(?:(\\d+)/(\\d+)|0 fnt)");
    private static final Pattern SPLIT_PATTERN  = Pattern.compile("^\\|split\\|(p[12])");
    private static final Pattern MISS_PATTERN   = Pattern.compile("^\\|-miss\\|((p[12][a-z]):[^|]*)\\|((p[12][a-z]):[^|]*)");
    private static final Pattern STATUS_PATTERN = Pattern.compile("^\\|(-boost|-unboost|-status|-sidestart|-weather|-fieldstart)\\|((p[12][a-z]):[^|]*)");
    private static final Pattern BOOST_SELF_PATTERN = Pattern.compile("^\\|-boost\\|((p[12][a-z]):[^|]*)");
    private static final Pattern HEAL_TRAP_PATTERN  = Pattern.compile("^\\|(-heal|-sidestart|-fieldstart|-weather)\\|");
    private static final ConcurrentHashMap<UUID, Integer> comboTracker = new ConcurrentHashMap<>();

    // làm căn cứ để cân bằng lượng exp nhận được
    private int getAnchorExpToNextEvolution(Pokemon att, Pokemon foe) {
        Pokemon anchor = (att.getLevel() <= foe.getLevel()) ? att : foe;
        int level = anchor.getLevel();
        int currentLevelBaseExp = anchor.getExperienceGroup().getExperience(level);
        int nextLevelBaseExp = anchor.getExperienceGroup().getExperience(level + 1);
        return Math.max(1, nextLevelBaseExp - currentLevelBaseExp);
    }

    // can thiệp vào tiến trình xử lý gói tin trận đấu
    @Inject(method = "interpret", at = @At("TAIL"), remap = false)
    private void onInterpretTail(PokemonBattle battle, String rawMessage, CallbackInfo ci) {
        if (battle == null || rawMessage == null || rawMessage.isEmpty()) return;
        if (!(battle instanceof IBattleData data)) return;

        ConcurrentHashMap<UUID, Double> budgetTracker = data.getBudgetTracker();
        ConcurrentHashMap<UUID, Integer> hpTracker    = data.getHpTracker();

        String[] lines = rawMessage.split("\n");
        String pendingAttackerSlot = null;
        String pendingMoveName     = null;
        String skipNextDamageForSlot = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || SPLIT_PATTERN.matcher(line).find()) continue;
            if (line.startsWith("|split|")) continue;

            if (line.startsWith("|move|")) {
                Matcher m = MOVE_PATTERN.matcher(line);
                if (m.find()) {
                    pendingAttackerSlot  = m.group(1);
                    pendingMoveName      = m.group(3).toLowerCase();
                    skipNextDamageForSlot = null;
                }
                continue;
            }

            if (line.startsWith("|-miss|") && pendingAttackerSlot != null) {
                Matcher m = MISS_PATTERN.matcher(line);
                if (m.find()) {
                    String targetFullId = m.group(3);
                    var targetPair = battle.getActorAndActiveSlotFromPNX(targetFullId);
                    var attPair    = battle.getActorAndActiveSlotFromPNX(pendingAttackerSlot);
                    if (targetPair != null && attPair != null) {
                        Pokemon attPkmn = attPair.getSecond().getBattlePokemon().getEffectedPokemon();
                        Pokemon foePkmn = targetPair.getSecond().getBattlePokemon().getEffectedPokemon();
                        int expToNext   = getAnchorExpToNextEvolution(attPkmn, foePkmn);
                        int missExp     = ExpConfig.missExp(expToNext);
                        missExp = ExpConfig.calcDefenderExp(missExp, attPkmn.getLevel(), foePkmn.getLevel());
                        int fMissExp = deductBudget(budgetTracker, foePkmn, attPkmn, missExp);

                        applyExpAndNotify(battle, targetPair.getSecond(), fMissExp,
                                "§d" + getOwnerPokemonName(targetPair.getSecond()) + " +" + fMissExp + " EXP", targetFullId);
                    }
                }
                continue;
            }

            if ((line.startsWith("|-unboost|") || line.startsWith("|-status|")) && pendingAttackerSlot != null) {
                Matcher m = STATUS_PATTERN.matcher(line);
                if (m.find()) {
                    String targetFullId = m.group(2);
                    String attSide    = pendingAttackerSlot.substring(0, 2);
                    String targetSide = targetFullId.substring(0, 2);

                    if (!attSide.equals(targetSide)) {
                        var attPair = battle.getActorAndActiveSlotFromPNX(pendingAttackerSlot);
                        if (attPair != null) {
                            Pokemon attPkmn = attPair.getSecond().getBattlePokemon().getEffectedPokemon();
                            var foePair = battle.getActorAndActiveSlotFromPNX(targetFullId);
                            Pokemon foePkmn = (foePair != null) ? foePair.getSecond().getBattlePokemon().getEffectedPokemon() : attPkmn;

                            int expToNext  = getAnchorExpToNextEvolution(attPkmn, foePkmn);
                            int debuffExp  = ExpConfig.debuffMoveExp(expToNext);
                            int fDebuffExp = deductBudget(budgetTracker, attPkmn, foePkmn, ExpConfig.calcAttackerExp(debuffExp, attPkmn.getLevel(), foePkmn.getLevel()));

                            applyExpAndNotify(battle, attPair.getSecond(), fDebuffExp,
                                    "§d" + getOwnerPokemonName(attPair.getSecond()) + " +" + fDebuffExp + " EXP", pendingAttackerSlot);              }
                    }
                }
                continue;
            }

            if (line.startsWith("|-boost|") && pendingAttackerSlot != null) {
                Matcher m = BOOST_SELF_PATTERN.matcher(line);
                if (m.find()) {
                    String boostedFullId = m.group(2);
                    String attSide     = pendingAttackerSlot.substring(0, 2);
                    String boostedSide = boostedFullId.substring(0, 2);

                    if (attSide.equals(boostedSide)) {
                        var attPair = battle.getActorAndActiveSlotFromPNX(pendingAttackerSlot);
                        if (attPair != null) {
                            Pokemon attPkmn = attPair.getSecond().getBattlePokemon().getEffectedPokemon();
                            Pokemon foePkmn = findFoePokemon(battle, pendingAttackerSlot);
                            if (foePkmn == null) foePkmn = attPkmn;

                            int expToNext = getAnchorExpToNextEvolution(attPkmn, foePkmn);
                            int buffExp   = ExpConfig.buffMoveExp(expToNext);
                            int fBuffExp  = deductBudget(budgetTracker, attPkmn, foePkmn, ExpConfig.calcAttackerExp(buffExp, attPkmn.getLevel(), foePkmn.getLevel()));

                            applyExpAndNotify(battle, attPair.getSecond(), fBuffExp,
                                    "§a" + getOwnerPokemonName(attPair.getSecond()) + " +" + fBuffExp + " EXP", pendingAttackerSlot);       }
                    }
                }
                continue;
            }

            if (HEAL_TRAP_PATTERN.matcher(line).find() && pendingAttackerSlot != null) {
                var attPair = battle.getActorAndActiveSlotFromPNX(pendingAttackerSlot);
                if (attPair != null) {
                    Pokemon attPkmn = attPair.getSecond().getBattlePokemon().getEffectedPokemon();
                    int expToNext   = getAnchorExpForAttacker(battle, attPkmn, pendingAttackerSlot);
                    int miscExp     = ExpConfig.miscMoveExp(expToNext);
                    int fMiscExp    = deductBudget(budgetTracker, attPkmn, attPkmn, ExpConfig.calcAttackerExp(miscExp, attPkmn.getLevel(), attPkmn.getLevel()));

                    applyExpAndNotify(battle, attPair.getSecond(), fMiscExp,
                            "§7" + getOwnerPokemonName(attPair.getSecond()) + " +" + fMiscExp + " EXP", pendingAttackerSlot);   }
                continue;
            }

            if (line.startsWith("|-damage|") || line.startsWith("|damage|")) {
                Matcher damageMatcher = DAMAGE_PATTERN.matcher(line);
                if (damageMatcher.find()) {
                    String targetFullId  = damageMatcher.group(1);
                    String targetSlotOnly = damageMatcher.group(2);

                    if (targetSlotOnly.equals(skipNextDamageForSlot)) {
                        skipNextDamageForSlot = null;
                        continue;
                    }

                    if (pendingAttackerSlot == null) continue;

                    var attackerPair = battle.getActorAndActiveSlotFromPNX(pendingAttackerSlot);
                    var targetPair   = battle.getActorAndActiveSlotFromPNX(targetFullId);

                    if (attackerPair == null || targetPair == null) {
                        skipNextDamageForSlot = targetSlotOnly;
                        continue;
                    }

                    ActiveBattlePokemon attacker = attackerPair.getSecond();
                    ActiveBattlePokemon target   = targetPair.getSecond();

                    Pokemon attPkmn = attacker.getBattlePokemon().getEffectedPokemon();
                    Pokemon foePkmn = target.getBattlePokemon().getEffectedPokemon();

                    int hpAfter;
                    int hpMax;

                    if (damageMatcher.group(3) != null) {
                        hpAfter = Integer.parseInt(damageMatcher.group(3));
                        hpMax   = Integer.parseInt(damageMatcher.group(4));
                    } else {
                        hpAfter = 0;
                        hpMax   = foePkmn.getMaxHealth();
                    }

                    int expToNext = getAnchorExpToNextEvolution(attPkmn, foePkmn);

                    if (hpAfter <= 0) {
                        hpTracker.put(foePkmn.getUuid(), hpAfter);
                        skipNextDamageForSlot = targetSlotOnly;
                        int faintBonus = ExpConfig.calcFaintBonus(expToNext);
                        int finalFaintExp = deductBudget(budgetTracker, attPkmn, foePkmn, faintBonus);
                        String winText = Component.translatable("cobblemon.battle.win")
                                .getString()
                                .replace("%1$s", "") // Xóa placeholder tên (ví dụ: Pikachu)
                                .replace("!", "")    // Xóa dấu chấm than
                                .replace(".", "")    // Xóa dấu chấm (như code cũ của bạn)
                                .trim();             // Xóa khoảng trắng thừa ở hai đầu
                        applyExpAndNotify(
                                battle,
                                attacker,
                                finalFaintExp,
                                "§e" + getOwnerPokemonName(attacker) + " " + winText + " +" + finalFaintExp + " EXP",
                                pendingAttackerSlot
                        );
                    }

                    Integer previousHP = hpTracker.put(foePkmn.getUuid(), hpAfter);
                    int hpBefore = (previousHP == null) ? foePkmn.getMaxHealth() : previousHP;
                    int dOut     = Math.max(0, hpBefore - hpAfter);
                    skipNextDamageForSlot = targetSlotOnly;

                    int finalAttExp, finalDefExp;
                    if (dOut <= 0) {
                        comboTracker.remove(attPkmn.getUuid());
                        continue;
                    } else {
                        int comboCount = comboTracker.merge(attPkmn.getUuid(), 1, Integer::sum);
                        boolean isCombo = comboCount > 1;

                        double attBase = isCombo
                                ? ExpConfig.comboHitExp(expToNext)
                                : ExpConfig.baseHitExp(expToNext);

                        double defBase = isCombo
                                ? ExpConfig.comboHitExp(expToNext)
                                : ExpConfig.baseHitExp(expToNext);

// Clamp damage ratio tránh overflow hoặc vượt HP
                        double ratio = Math.max(0.0, Math.min(1.0, (double) dOut / hpMax));

// Weight riêng cho attacker và defender
                        double aWeight = ExpConfig.attackerWeight(dOut, hpMax);
                        double dWeight = ExpConfig.defenderWeight(dOut, hpMax);

// Attacker:
// tối thiểu 50% exp
// damage càng cao càng tăng
                        finalAttExp = ExpConfig.calcAttackerExp(
                                (int) (attBase * (0.5 + aWeight)),
                                attPkmn.getLevel(),
                                foePkmn.getLevel()
                        );

// Defender:
// chỉ nhận ít exp khi tank tốt
                        finalDefExp = ExpConfig.calcDefenderExp(
                                (int) (defBase * (0.3 * dWeight)),
                                attPkmn.getLevel(),
                                foePkmn.getLevel()
                        );    }

                    int fAttExp = deductBudget(budgetTracker, attPkmn, foePkmn, finalAttExp);
                    int fDefExp = deductBudget(budgetTracker, foePkmn, attPkmn, finalDefExp);

                    applyExpAndNotify(battle, attacker, fAttExp, "§6" + getOwnerPokemonName(attacker) + " +" + fAttExp + " EXP", pendingAttackerSlot);
                    applyExpAndNotify(battle, target, fDefExp, "§b" + getOwnerPokemonName(target) + " +" + fDefExp + " EXP", targetFullId);
                }
            }
        }
    }

    // dự phòng tính exp khi không xác định được đối thủ
    private int getAnchorExpForAttacker(PokemonBattle battle, Pokemon attPkmn, String attSlot) {
        int current   = attPkmn.getExperience();
        int nextLvExp = attPkmn.getExperienceGroup().getExperience(attPkmn.getLevel() + 1);
        return Math.max(1, nextLvExp - current);
    }

    // xác định mục tiêu để tính toán chênh lệch cấp độ
    private Pokemon findFoePokemon(PokemonBattle battle, String attSlot) {
        String attSide = attSlot.substring(0, 2);
        var foeSide = attSide.equals("p1") ? battle.getSide2() : battle.getSide1();
        if (foeSide == null) return null;
        for (var actor : foeSide.getActors()) {
            for (var activePkmn : actor.getActivePokemon()) {
                if (activePkmn.getBattlePokemon() != null) {
                    return activePkmn.getBattlePokemon().getEffectedPokemon();
                }
            }
        }
        return null;
    }

    // hiển thị thông báo rõ ràng cho từng người chơi
    private String getOwnerPokemonName(ActiveBattlePokemon activePkmn) {
        String pkmnName = activePkmn.getBattlePokemon().getEffectedPokemon().getDisplayName(true).getString();
        if (activePkmn.getActor() instanceof PlayerBattleActor playerActor) {
            ServerPlayer player = playerActor.getEntity();
            if (player != null) return player.getScoreboardName() + " " + pkmnName;
        }
        return pkmnName;
    }

    // đồng bộ hóa dữ liệu kinh nghiệm giữa server và client
    private void applyExpAndNotify(PokemonBattle battle, ActiveBattlePokemon activePkmn,
                                   int amount, String message, String fullId) {
        if (amount <= 0 || activePkmn == null) return;
        Pokemon pokemon = activePkmn.getBattlePokemon().getEffectedPokemon();
        if (pokemon == null || pokemon.getLevel() >= 100) return;

        int oldLevel = pokemon.getLevel();

        if (activePkmn.getActor() instanceof PlayerBattleActor playerActor) {
            ServerPlayer ownerPlayer = playerActor.getEntity();
            if (ownerPlayer != null) {
                givePlayerExp(pokemon, ownerPlayer, amount);
            }
        } else {
            giveWildExp(pokemon, amount);
        }

        if (pokemon.getLevel() > oldLevel) {
            LevelOverrideTracker.setLevel(pokemon.getUuid(), pokemon.getLevel());
            LevelOverrideTracker.setMaxHp(pokemon.getUuid(), pokemon.getMaxHealth());
            // Không cộng thêm currentHealth — chỉ cần maxHP tăng, HP hiện tại giữ nguyên
            pokemon.updateAspects();
            activePkmn.getBattlePokemon().sendUpdate();

            if (activePkmn.getActor() instanceof PlayerBattleActor playerActor) {
                ServerPlayer ownerPlayer = playerActor.getEntity();
                if (ownerPlayer != null) {
                    CobblemonNetwork.INSTANCE.sendPacketToPlayer(ownerPlayer, new BattleUpdateTeamPokemonPacket(pokemon));
                }
            }

            // Cập nhật level + species cho Showdown client
            battle.writeShowdownAction("|detailschange|" + activePkmn.getPNX() + "|" + pokemon.getSpecies().getName() + " L" + pokemon.getLevel());

            // ✅ FIX: Gọi sendUpdate() lại SAU detailschange để client nhận đúng maxHp mới.
            // Cobblemon client đọc maxHp từ BattlePokemon packet — không phải từ Showdown message.
            // Gọi lần đầu ở trên có thể bị client xử lý trước detailschange → maxHp vẫn cũ.
            activePkmn.getBattlePokemon().sendUpdate();
        }
        if (battle.getSide1() != null) battle.getSide1().broadcastChatMessage(Component.literal(message));
        if (battle.getSide2() != null) battle.getSide2().broadcastChatMessage(Component.literal(message));
    }

    // đảm bảo cơ chế cộng exp đúng chuẩn người chơi
    private void givePlayerExp(Pokemon pokemon, ServerPlayer player, int amount) {
        ExperienceSource battleSource = new ExperienceSource() {
            @Override public boolean isBattle() { return true; }
        };
        pokemon.addExperienceWithPlayer(player, battleSource, amount);
    }

    // xử lý riêng việc tăng cấp cho pokemon không chủ
    private void giveWildExp(Pokemon pokemon, int amount) {
        ExperienceSource battleSource = new ExperienceSource() {
            @Override public boolean isBattle() { return true; }
        };
        pokemon.addExperience(battleSource, amount);
    }

    // ngăn chặn việc lạm dụng cày cấp quá nhanh
    private int deductBudget(ConcurrentHashMap<UUID, Double> tracker,
                             Pokemon p, Pokemon foe, int amount) {
        final int[] actualExp = { amount };

        tracker.compute(p.getUuid(), (uuid, current) -> {
            if (current == null) {
                Pokemon anchor = (p.getLevel() <= foe.getLevel()) ? p : foe;
                int anchorCurrent  = anchor.getExperience();
                int anchorNextLvExp = anchor.getExperienceGroup().getExperience(anchor.getLevel() + 1);
                int expToNext = Math.max(1, anchorNextLvExp - anchorCurrent);
                current = ExpConfig.calculateBattleBudget(expToNext);
            }

            if (current <= 0) {
                actualExp[0] = ExpConfig.getOverBudgetExp();
                return current;
            }

            int granted = Math.min(amount, current.intValue());
            actualExp[0] = granted;
            return current - granted;
        });

        return actualExp[0];
    }
}