package eu.darkbot.popcorn.def;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Ship;
import eu.darkbot.api.game.enums.EntityEffect;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.I18nAPI;
import eu.darkbot.api.managers.RepairAPI;
import eu.darkbot.shared.legacy.LegacyModuleAPI;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Feature(name = "Anti push", description = "Turns off the bot if an enemy uses draw fire or is killed over X times by the same player", enabledByDefault = true)
public class AntiPush implements Behavior, Configurable<AntiPush.Config> {

    private final HeroAPI hero;
    private final BotAPI bot;
    private final RepairAPI repairManager;
    private final I18nAPI i18n;
    private final LegacyModuleAPI legacyModules;

    private final Collection<? extends Ship> ships;
    private Config config;

    // key: user id, value: List of times of death, List::size() is death count
    private final Map<Integer, List<Instant>> deathStats = new HashMap<>();
    private boolean wasDead = true;

    @Configuration("anti_push.config")
    public static class Config {
        public @Number(min = -1, max = 300) int DRAWFIRE_PAUSE_TIME = -1;
        public @Number(min = 1, max = 1000, step = 1) int MAX_DEATHS = 7;
        public @Number(min = -1, max = 300) int DEATH_PAUSE_TIME = -1;
    }

    public AntiPush(HeroAPI hero,
                    BotAPI bot,
                    RepairAPI repairManager,
                    I18nAPI i18n,
                    LegacyModuleAPI legacyModules,
                    EntitiesAPI entities,
                    AuthAPI auth) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) throw new SecurityException();
        VerifierChecker.checkAuthenticity(auth);

        this.hero = hero;
        this.bot = bot;
        this.repairManager = repairManager;
        this.i18n = i18n;
        this.legacyModules = legacyModules;

        this.ships = entities.getShips();
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.config = config.getValue();
    }

    @Override
    public void onTickBehavior() {
        tickDrawFire();
        tickDeathPause();
    }

    // updating deathStats on tickStopped() because behaviour not ticked when ship isDead()
    @Override
    public void onStoppedBehavior() {
        if (config.DEATH_PAUSE_TIME == 0) return;

        removeOldDeaths();
        if (repairManager.isDestroyed() && !wasDead) {
            ships.stream()
                    .filter(s -> s.getEntityInfo().getUsername().equals(repairManager.getLastDestroyerName()))
                    .findFirst()
                    .ifPresent(killer -> deathStats.computeIfAbsent(
                            killer.getId(), l -> new ArrayList<>()).add(Instant.now()));
            wasDead = true;
        }
    }

    private void tickDrawFire() {
        if (config.DRAWFIRE_PAUSE_TIME == 0) return;

        for (Ship ship : ships) {
            if (!ship.getEntityInfo().isEnemy() || !ship.hasEffect(EntityEffect.DRAW_FIRE) || hero.getTarget() != ship) continue;
            System.out.println("Pausing bot" +
                    (config.DRAWFIRE_PAUSE_TIME > 0 ? " for " + config.DRAWFIRE_PAUSE_TIME + " minutes" : "") +
                    ", enemy used draw fire");

            Long pauseMillis = config.DRAWFIRE_PAUSE_TIME > 0 ? (long) config.DRAWFIRE_PAUSE_TIME * 60 * 1000 : null;
            bot.setModule(legacyModules.getDisconnectModule(pauseMillis, i18n.get("module.disconnect.reason.draw_fire")));
        }
    }

    private void tickDeathPause() {
        if (config.DEATH_PAUSE_TIME == 0) return;
        wasDead = false;

        deathStats.entrySet().stream()
                .filter(e -> e.getValue().size() >= config.MAX_DEATHS)
                .findFirst()
                .ifPresent(entry -> {
                    String killer = repairManager.getLastDestroyerName();
                    if (killer == null) killer = "Unknown";

                    System.out.format("Pausing for %d minutes (Death pause feature): killed by %s %d times\n",
                            config.DEATH_PAUSE_TIME, killer, entry.getValue().size());
                    bot.setModule(legacyModules.getDisconnectModule(config.DEATH_PAUSE_TIME > 0 ? config.DEATH_PAUSE_TIME * 60 * 1000L : null,
                            i18n.get("module.disconnect.reason.death_pause", killer, entry.getValue().size())));
                    deathStats.remove(entry.getKey());
                });
    }

    private void removeOldDeaths() {
        deathStats.values()
                .forEach(time -> time.removeIf(t -> Duration.between(t, Instant.now()).toDays() >= 1));
        deathStats.values().removeIf(List::isEmpty);
    }
}
