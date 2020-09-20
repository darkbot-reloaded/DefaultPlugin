package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.entities.BasePoint;
import com.github.manolo8.darkbot.core.itf.InstructionProvider;
import com.github.manolo8.darkbot.core.manager.StatsManager;
import com.github.manolo8.darkbot.core.objects.Map;
import com.github.manolo8.darkbot.core.objects.OreTradeGui;
import com.github.manolo8.darkbot.core.utils.Location;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.LootNCollectorModule;
import com.github.manolo8.darkbot.modules.MapModule;

import java.util.Arrays;
import java.util.List;

@Feature(name = "Palladium Module", description = "Loot & collect, but when full cargo is full travels to 5-2 to sell")
public class PalladiumModule extends LootNCollectorModule implements InstructionProvider {

    private Map SELL_MAP;

    private Main main;
    private StatsManager statsManager;
    private List<BasePoint> bases;
    private OreTradeGui oreTrade;

    private long sellClick;

    @Override
    public String instructions() {
        return "Recommended settings:\n" +
                "General -> Working map to 5-3\n" +
                "Collect -> Set ore_8 wait to 750-800ms (depends on ping)\n" +
                "Npc killer -> Pirate NPCs -> Kill & Enable Passive (in the Extra Column)\n" +
                "Avoid zones -> Set in all areas of 5-3 except palladium field & paths to portals\n" +
                "Preferred zones -> Set Preferred zone in palladium field\n" +
                "General -> Roaming & Preferred area -> enable only kill npcs in preferred area\n" +
                "Safety places -> Set Portals to jump: Never.\n" +
                "Npc killer -> Battleray -> Set low priority (100) so Interceptors are shot first";
    }

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();
        super.install(main);
        this.SELL_MAP = main.starManager.byName("5-2");

        this.main = main;
        this.statsManager = main.statsManager;
        this.bases = main.mapManager.entities.basePoints;
        this.oreTrade = main.guiManager.oreTrade;
    }

    @Override
    public void tick() {
        if (statsManager.deposit >= statsManager.depositTotal && statsManager.depositTotal != 0) sell();
        else if (System.currentTimeMillis() - 500 > sellClick && oreTrade.showTrade(false, null)) super.tick();
    }

    private void sell() {
        pet.setEnabled(false);
        if (hero.map != SELL_MAP) main.setModule(new MapModule()).setTarget(SELL_MAP);
        else bases.stream().filter(b -> b.locationInfo.isLoaded()).findFirst().ifPresent(base -> {
            if (drive.movingTo().distance(base.locationInfo.now) > 200) { // Move to base
                double angle = base.locationInfo.now.angle(hero.locationInfo.now) + Math.random() * 0.2 - 0.1;
                drive.move(Location.of(base.locationInfo.now, angle, 100 + (100 * Math.random())));
            } else if (!hero.locationInfo.isMoving() && oreTrade.showTrade(true, base)
                    && System.currentTimeMillis() - 60_000 > sellClick) {
                oreTrade.sellOre(OreTradeGui.Ore.PALLADIUM);
                sellClick = System.currentTimeMillis();
            }
        });
    }

}