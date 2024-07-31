package eu.darkbot.popcorn.def;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.InstructionProvider;
import eu.darkbot.api.game.entities.Station;
import eu.darkbot.api.game.other.GameMap;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.OreAPI;
import eu.darkbot.api.managers.StarSystemAPI;
import eu.darkbot.api.managers.StatsAPI;
import eu.darkbot.shared.modules.LootCollectorModule;
import eu.darkbot.shared.modules.MapModule;

import java.util.Arrays;
import java.util.Collection;

@Feature(name = "Palladium Module", description = "Loot & collect, but when cargo is full, travels to 5-2 to sell")
public class PalladiumModule extends LootCollectorModule implements InstructionProvider {

    private final PluginAPI api;
    private final BotAPI bot;
    private final StatsAPI stats;
    private final OreAPI ores;

    private final GameMap SELL_MAP;
    private final Collection<? extends Station> bases;

    private long lastSellAttempt;

    public PalladiumModule(PluginAPI api,
                           BotAPI bot,
                           StatsAPI stats,
                           OreAPI ores,
                           EntitiesAPI entities,
                           StarSystemAPI starSystem,
                           AuthAPI auth) throws StarSystemAPI.MapNotFoundException {
        super(api);
        authenticate(auth);
        this.api = api;
        this.bot = bot;
        this.stats = stats;
        this.ores = ores;
        this.SELL_MAP = starSystem.getByName("5-2");
        this.bases = entities.getStations();
    }

    private void authenticate(AuthAPI auth) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners()))
            throw new SecurityException();
        VerifierChecker.checkAuthenticity(auth);
    }

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
    public void onTickModule() {
        if (isCargoFull()) {
            sell();
        } else if (canCollectPalladium()) {
            super.onTickModule();
        }
    }

    private boolean isCargoFull() {
        return stats.getCargo() >= stats.getMaxCargo() && stats.getMaxCargo() != 0;
    }

    private boolean canCollectPalladium() {
        return System.currentTimeMillis() - 500 > lastSellAttempt && ores.showTrade(false, null);
    }

    private void sell() {
        pet.setEnabled(false);
        if (hero.getMap() != SELL_MAP) {
            travelToSellMap();
            return;
        }
        Station.Refinery base = findRefineryBase();
        if (base == null) return;

        if (shouldMoveToBase(base)) {
            moveToBase(base);
        } else if (canSellOre(base)) {
            sellOre();
        }
    }

    private void travelToSellMap() {
        bot.setModule(api.requireInstance(MapModule.class)).setTarget(SELL_MAP);
    }

    private Station.Refinery findRefineryBase() {
        return bases.stream()
                .filter(b -> b instanceof Station.Refinery && b.getLocationInfo().isInitialized())
                .map(Station.Refinery.class::cast)
                .findFirst().orElse(null);
    }

    private boolean shouldMoveToBase(Station.Refinery base) {
        return movement.getDestination().distanceTo(base) > 200;
    }

    private void moveToBase(Station.Refinery base) {
        double angle = base.angleTo(hero) + Math.random() * 0.2 - 0.1;
        movement.moveTo(Location.of(base, angle, 100 + (100 * Math.random())));
    }

    private boolean canSellOre(Station.Refinery base) {
        return !hero.isMoving() && ores.showTrade(true, base) && System.currentTimeMillis() - 60_000 > lastSellAttempt;
    }

    private void sellOre() {
        ores.sellOre(OreAPI.Ore.PALLADIUM);
        lastSellAttempt = System.currentTimeMillis();
    }
}
