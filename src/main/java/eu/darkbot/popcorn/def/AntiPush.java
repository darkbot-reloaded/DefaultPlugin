package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.types.Num;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.entities.Ship;
import com.github.manolo8.darkbot.core.itf.Behaviour;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.manager.EffectManager;
import com.github.manolo8.darkbot.core.manager.MapManager;
import com.github.manolo8.darkbot.core.manager.RepairManager;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.DisconnectModule;
import com.github.manolo8.darkbot.utils.I18n;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Feature(name = "Anti push", description = "Turns off the bot if an enemy uses draw fire or is killed over X times by the same player", enabledByDefault = true)
public class AntiPush implements Behaviour, Configurable<AntiPush.Config> {

    private MapManager mapManager;
    private RepairManager repairManager;
    private List<Ship> ships;
    private Main main;
    private Config config;

    // key: user id, value: List of times of death, List::size() is death count
    private final Map<Integer, List<Instant>> deathStats = new HashMap<>();
    private boolean wasDead = true;

    public static class Config {
        @Option(value = "Pause time on draw fire (minutes)", description = "Pause time, 0 to disable feature, -1 for infinite pause")
        @Num(min = -1, max = 300)
        public int DRAWFIRE_PAUSE_TIME = -1;

        @Option(value = "Max kills by same player", description = "The maximum times one player can kill you before bot pauses.")
        @Num(min = 1, max = 1000, step = 1)
        public int MAX_DEATHS = 7;

        @Option(value = "Pause time after kills reached (minutes)", description = "Time to pause after kills reached, 0 to disable feature, -1 for infinite pause")
        @Num(min = -1, max = 300)
        public int DEATH_PAUSE_TIME = -1;
    }

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();
        this.main = main;
        this.mapManager = main.mapManager;
        this.ships = main.mapManager.entities.ships;
        this.repairManager = main.repairManager;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public void tick() {
        tickDrawFire();
        tickDeathPause();
    }

    // updating deathStats on tickStopped() because behaviour not ticked when ship isDead()
    @Override
    public void tickStopped() {
        if (config.DEATH_PAUSE_TIME == 0) return;

        removeOldDeaths();
        if (repairManager.isDead() && !wasDead) {
            ships.stream()
                    .filter(s -> s.playerInfo.username.equals(repairManager.getKillerName()))
                    .findFirst()
                    .ifPresent(killer -> deathStats.computeIfAbsent(
                            killer.id, l -> new ArrayList<>()).add(Instant.now()));
            wasDead = true;
        }
    }

    private void tickDrawFire() {
        if (config.DRAWFIRE_PAUSE_TIME == 0) return;

        for (Ship ship : ships) {
            if (!ship.playerInfo.isEnemy() || !ship.hasEffect(EffectManager.Effect.DRAW_FIRE) || !mapManager.isTarget(ship)) continue;
            System.out.println("Pausing bot" +
                    (config.DRAWFIRE_PAUSE_TIME > 0 ? " for " + config.DRAWFIRE_PAUSE_TIME + " minutes" : "") +
                    ", enemy used draw fire");

            Long pauseMillis = config.DRAWFIRE_PAUSE_TIME > 0 ? (long) config.DRAWFIRE_PAUSE_TIME * 60 * 1000 : null;
            main.setModule(new DisconnectModule(pauseMillis, I18n.get("module.disconnect.reason.draw_fire")));
        }
    }

    private void tickDeathPause() {
        if (config.DEATH_PAUSE_TIME == 0) return;
        wasDead = false;

        deathStats.entrySet().stream()
                .filter(e -> e.getValue().size() >= config.MAX_DEATHS)
                .findFirst()
                .ifPresent(entry -> {
                    System.out.format("Pausing for %d minutes (Death pause feature): killed by %s %d times\n",
                            config.DEATH_PAUSE_TIME,
                            repairManager.getKillerName(),
                            entry.getValue().size());
                    main.setModule(new DisconnectModule(config.DEATH_PAUSE_TIME > 0 ? config.DEATH_PAUSE_TIME * 60 * 1000L : null,
                            I18n.get("module.disconnect.reason.death_pause",
                                    repairManager.getKillerName(),
                                    entry.getValue().size())));
                    deathStats.remove(entry.getKey());
                });
    }

    private void removeOldDeaths() {
        deathStats.values()
                .forEach(time -> time.removeIf(t -> Duration.between(t, Instant.now()).toDays() >= 1));
        deathStats.values().removeIf(List::isEmpty);
    }
}
