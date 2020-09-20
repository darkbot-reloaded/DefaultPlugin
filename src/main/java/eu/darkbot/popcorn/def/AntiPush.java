package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.types.Num;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.entities.Ship;
import com.github.manolo8.darkbot.core.itf.Behaviour;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.manager.EffectManager;
import com.github.manolo8.darkbot.core.manager.MapManager;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.DisconnectModule;
import com.github.manolo8.darkbot.utils.I18n;

import java.util.Arrays;
import java.util.List;

@Feature(name = "Anti push", description = "Turns off the bot if an enemy uses draw fire", enabledByDefault = true)
public class AntiPush implements Behaviour, Configurable<AntiPush.Config> {

    private MapManager mapManager;
    private List<Ship> ships;
    private Main main;
    private Config config;

    public static class Config {
        @Option(value = "Pause Time (minutes)", description = "Pause time, 0 for infinite pause")
        @Num(min = 0, max = 300)
        public int PAUSE_TIME = 0;
    }

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();
        this.main = main;
        this.mapManager = main.mapManager;
        this.ships = main.mapManager.entities.ships;
    }

    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public void tick() {
        for (Ship ship : ships) {
            if (!ship.playerInfo.isEnemy() || !ship.hasEffect(EffectManager.Effect.DRAW_FIRE) || !mapManager.isTarget(ship)) continue;
            System.out.println("Pausing bot" +
                    (config.PAUSE_TIME > 0 ? " for " + config.PAUSE_TIME + " minutes" : "") +
                    ", enemy used draw fire");

            Long pauseMillis = config.PAUSE_TIME > 0 ? (long) config.PAUSE_TIME * 60 * 1000 : null;
            main.setModule(new DisconnectModule(pauseMillis, I18n.get("module.disconnect.reason.draw_fire")));
        }
    }

}