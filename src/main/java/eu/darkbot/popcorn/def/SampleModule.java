package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.NpcExtraFlag;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Num;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.itf.ExtraMenuProvider;
import com.github.manolo8.darkbot.core.itf.Module;
import com.github.manolo8.darkbot.core.itf.NpcExtraProvider;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.gui.tree.components.JPercentField;
import com.github.manolo8.darkbot.gui.utils.Popups;
import com.github.manolo8.darkbot.utils.AuthAPI;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

@Feature(name = "Sample module", description = "Module that does nothing, just to show how to create a module")
public class SampleModule implements
        Module, // This, is a module, and should appear to be picked in the list of modules
        Configurable<SampleModule.SampleConfig>, // The module has a configuration, of the class SampleModule.SampleConfig
        NpcExtraProvider, // The module provides extra flags for npcs, they'll apear in the flags column
        ExtraMenuProvider { // The module provides extra buttons for the main menu in dark bot


    // Module

    private Drive drive;
    /**
     * The install method, provides you with an instance of Main that you should "install" for, this means
     * that if you want any info from main (like what ships are on the map) you should get it from this main.
     * You'll often want to save this on a field to use on the tick() method later.
     * @param main The main to use
     */
    @Override
    public void install(Main main) {
        // Ensure that the verifier is from this plugin and properly signed by yourself
        // If it isn't don't install for this main.
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;

        AuthAPI api = VerifierChecker.getAuthApi();

        // If the user isn't verified, setup auth.
        // This should be done regardless of if you enforce donors-only on your modules
        if (!api.isAuthenticated()) api.setupAuth();

        // Alternatively if you want to enforce donors-only on your features, use this instead
        // You should not do both, as requireDonor will take care of setting up auth
        //if (!VerifierChecker.getAuthApi().requireDonor()) return;


        this.drive = main.hero.drive;
    }

    /**
     * Always allow refreshing. The currently installed module is what dictates if refreshing is allowed
     * @return always true. Sample module never blocks refreshes
     */
    @Override
    public boolean canRefresh() {
        return true;
    }

    /**
     * The status to display in the bot. The module controls this and it's your way to display feedback
     * to the user about what the module is doing or what the state is
     * @return The status of the module
     */
    @Override
    public String status() {
        return "Sample module - Moving : " + config.MOVE_SHIP +
                " - " + (config.PERCENTAGE_VALUE * 100) + "%";
    }

    /**
     * This is the main logic function. This will be called every tick that your module
     * is running for, usually every ~15ms, but you should not rely on timing.
     * The function MUST be non-blocking. No Thread.sleep or calls to the internet.
     * If you need to perform blocking operations you should look into Tasks, not modules.
     *
     * Note: If you implement BOTH module and task/behaviour, it is best to create an empty tick function,
     * and implement tickModule, tickBehaviour or tickTask separately. Otherwise the same tick function
     * could be calling from all 3 places and you do not know which is which.
     */
    @Override
    public void tick() {
        // Simple logic, just move randomly if the config checked MOVE_SHIP
        // moveRandom will already perform all logic of preferred zones
        if (config.MOVE_SHIP && !drive.isMoving()) drive.moveRandom();
    }


    // Configurable

    /**
     * The configuration class, with all the things that can be configured by the user for this module
     * This just shows a few examples of bundled editors for basic data types
     *
     * For more examples on what editors are available, check the main Config.java file on DarkBot
     */
    public static class SampleConfig {
        @Option("Some boolean checkbox")
        public boolean BOOLEAN_VALUE = true;

        @Option("Integer limited to 1-999")
        @Num(min = 1, max = 999)
        public int INTEGER_LIMIT = 1;

        @Option("% value, can be used for health")
        @Editor(JPercentField.class)
        public double PERCENTAGE_VALUE = 0.5; // Ranges from 0 to 1, 75% = 0.75

        @Option("Hotkey to press")
        public Character key;

        @Option("Move ship")
        public boolean MOVE_SHIP;
    }

    // Field to save your config instance
    private SampleConfig config;

    /**
     * The method will be called from outside, providing the config to use, saving it in a field
     * @param config The config this module should use
     */
    @Override
    public void setConfig(SampleConfig config) {
        this.config = config;
    }


    // Extra npc flags

    /**
     * Provide all the NpcExtraFlags in the method
     */
    @Override
    public NpcExtraFlag[] values() {
        return Extra.values();
    }

    /**
     * The easiest way of making flags, is an enum implementing the interface,
     * where you can add as many entries as you wish.
     * Then from the config it's easy to check if npcInfo#has(Extra.WEIRD_FLAG)
     */
    private enum Extra implements NpcExtraFlag {
        SAMPLE_FLAG("SF", "Sample flag", "Sample flag for testing purposes, provided by sample module");

        private final String shortName, name, description;
        Extra(String shortName, String name, String description) {
            this.shortName = shortName;
            this.name = name;
            this.description = description;
        }

        @Override
        public String getShortName() {
            return shortName;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    // Extra menu provider

    /**
     * Return the menu items to show in the extra menu.
     * @param main The main to create menu items for. You may not need it
     * @return The list of menu items to add
     */
    @Override
    public Collection<JComponent> getExtraMenuItems(Main main) {
        return Arrays.asList(
                createSeparator("Sample"),
                create("Sample menu", e -> Popups.showMessageAsync(
                        "Sample popup",
                        "Just a pop-up created when pressing the sample module",
                        JOptionPane.INFORMATION_MESSAGE)));
    }
}
