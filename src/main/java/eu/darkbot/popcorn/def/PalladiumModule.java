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

@Feature(name = "Palladium Module", description = "Loot & collect, but when full cargo is full travels to 5-2 to sell")
public class PalladiumModule extends LootCollectorModule implements InstructionProvider {


    private final PluginAPI api;
    private final BotAPI bot;
    private final StatsAPI stats;
    private final OreAPI ores;

    private final GameMap SELL_MAP;
    private final Collection<? extends Station> bases;

    private long sellClick;

    public PalladiumModule(PluginAPI api,
                           BotAPI bot,
                           StatsAPI stats,
                           OreAPI ores,
                           EntitiesAPI entities,
                           StarSystemAPI starSystem,
                           AuthAPI auth) throws StarSystemAPI.MapNotFoundException {
        super(api);
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) throw new SecurityException();
        VerifierChecker.checkAuthenticity(auth);

        this.api = api;
        this.bot = bot;
        this.stats = stats;
        this.ores = ores;

        this.SELL_MAP = starSystem.getByName("5-2");
        this.bases = entities.getStations();
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
        if (stats.getCargo() >= stats.getMaxCargo() && stats.getMaxCargo() != 0) sell();
        else if (System.currentTimeMillis() - 500 > sellClick && ores.showTrade(false, null)) super.onTickModule();
    }

    private void sell() {
        pet.setEnabled(false);
        if (hero.getMap() != SELL_MAP) {
            bot.setModule(api.requireInstance(MapModule.class)).setTarget(SELL_MAP);
            return;
        }
        Station.Refinery base = bases.stream()
                .filter(b -> b instanceof Station.Refinery && b.getLocationInfo().isInitialized())
                .map(Station.Refinery.class::cast)
                .findFirst().orElse(null);
        if (base == null) return;

        if (movement.getDestination().distanceTo(base) > 200) { // Move to base
            double angle = base.angleTo(hero) + Math.random() * 0.2 - 0.1;
            movement.moveTo(Location.of(base, angle, 100 + (100 * Math.random())));
        } else if (!hero.isMoving() && ores.showTrade(true, base)
                && System.currentTimeMillis() - 60_000 > sellClick) {
            ores.sellOre(OreAPI.Ore.PALLADIUM);
            sellClick = System.currentTimeMillis();
        }
    }

}