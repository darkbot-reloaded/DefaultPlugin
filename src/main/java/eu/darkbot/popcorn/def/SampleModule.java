package eu.darkbot.popcorn.def;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Percentage;
import eu.darkbot.api.config.types.PercentRange;
import eu.darkbot.api.config.types.ShipMode;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.ExtraMenus;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Module;
import eu.darkbot.api.extensions.NpcFlags;
import eu.darkbot.api.extensions.PluginInfo;
import eu.darkbot.api.game.items.SelectableItem;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.ExtensionsAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.I18nAPI;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.util.Popups;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

@Feature(name = "Sample module", description = "Module that does nothing, just to show how to create a module")
public class SampleModule implements
        Module, // This, is a module, and should appear to be picked in the list of modules
        Configurable<SampleModule.SampleConfig>, // The module has a configuration, of the class SampleModule.SampleConfig
        NpcFlags<SampleModule.Extra>, // The module provides extra flags for npcs, they'll apear in the flags column
        ExtraMenus { // The module provides extra buttons for the main menu in dark bot

    private final MovementAPI movement;

    /**
     * The constructor, provides you with instances to all the apis you want to make use of
     * You can check the classes in {@link eu.darkbot.api.managers} package to see the different apis available
     * @param movement The movement API used to move the ship around
     */
    public SampleModule(MovementAPI movement, AuthAPI auth) {
        // Ensure that the verifier is from this plugin and properly signed by yourself
        // If it isn't, fail with a security exception.
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners()))
            throw new SecurityException();
        VerifierChecker.verifyAuthApi(auth);


        // If the user isn't verified, setup auth.
        // This should be done regardless of if you enforce donors-only on your modules
        if (!auth.isAuthenticated()) auth.setupAuth();

        // Alternatively if you want to enforce donors-only on your features, use this instead
        // You should not do both, as requireDonor will take care of setting up auth
        //if (!auth.requireDonor()) return;


        this.movement = movement;
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
    public String getStatus() {
        return "Sample module - Moving : " + config.MOVE_SHIP +
                " - " + (config.PERCENTAGE_VALUE * 100) + "%";
    }

    /**
     * This is the main logic function. This will be called every tick that your module
     * is running for, usually every ~15ms, but you should not rely on timing.
     * The function MUST be non-blocking. No Thread.sleep or calls to the internet.
     * If you need to perform blocking operations you should look into Tasks, not modules.
     */
    @Override
    public void onTickModule() {
        // Simple logic, just move randomly if the config checked MOVE_SHIP
        // moveRandom will already perform all logic of preferred zones
        if (config.MOVE_SHIP && !movement.isMoving()) movement.moveRandom();
    }


    // Configurable

    /**
     * The configuration class, with all the things that can be configured by the user for this module
     * This just shows a few examples of bundled editors for basic data types
     *
     * For more examples on what editors are available, check the main Config.java file on DarkBot
     *
     * To set the names of the configs, create a resource file strings_en.properties in the proper package, and
     * set them with the proper keys. You can create different resource files for different languages.
     *
     * All fields will be considered configs by default, you can set allOptions to false in the
     * Configuration annotation to disable this and then add @Option to each field you want to be an option.
     * You can exclude fields by annotating them with @Option.Ignore
     */
    @Configuration("sample_module.config")
    public static class SampleConfig {
        public boolean BOOLEAN_VALUE = true;

        @Number(min = 1, max = 999)
        public int INTEGER_LIMIT = 1;

        @Percentage
        public double PERCENTAGE_VALUE = 0.5; // Ranges from 0 to 1, 75% = 0.75

        public Character key;

        public boolean MOVE_SHIP;

        public ShipMode SHIP_MODE = ShipMode.of(HeroAPI.Configuration.FIRST, SelectableItem.Formation.HEART);

        public PercentRange PERCENT_RANGE = PercentRange.of(0.5, 0.75);
    }

    // Field to save your config instance
    private SampleConfig config;

    /**
     * The method will be called from outside, providing the config to use for this.
     * The object instance for configuration will be inside config.getValue, however,
     * the object instance will act as a read-only object, where changes will not be
     * reflected back to the user in the config tree.
     * To make meaningful changes to the configuration (visible to the user) use
     * setValue on the config setting instead.
     *
     * @param config The config this module should use
     */
    @Override
    public void setConfig(ConfigSetting<SampleConfig> config) {
        this.config = config.getValue();
    }


    // Extra npc flags

    /**
     * Define an enum that is used as the generic parameter
     * where you can add as many entries as you wish.
     * Then from the config it's easy to check if npcInfo#hasExtraFlag(Extra.WEIRD_FLAG)
     */
    @Configuration("sample_module.flags")
    public enum Extra {
        SAMPLE_FLAG
    }

    // Extra menu provider

    /**
     * Return the menu items to show in the extra menu.
     * @param api The api to create menu items for.
     * @return The list of menu items to add
     */
    @Override
    public Collection<JComponent> getExtraMenuItems(PluginAPI api) {
        // Get i18n to be able to translate
        I18nAPI i18n = api.requireAPI(I18nAPI.class);
        // Get extensions api to get a plugin
        ExtensionsAPI extensionsAPI = api.requireAPI(ExtensionsAPI.class);
        // Find the plugin info for our own class
        PluginInfo plugin = extensionsAPI.getFeatureInfo(getClass()).getPluginInfo();

        return Arrays.asList(
                // Use i18n with the plugin as context to find translations in our plugin resource file
                createSeparator(i18n.get(plugin, "sample_module.menu.separator")),
                create(i18n.get(plugin, "sample_module.menu.menu_entry"), e -> Popups.showMessageAsync(
                        "Sample popup", // These could also come from translation file, but for simplicity they don't
                        "Just a pop-up created when pressing the sample module",
                        JOptionPane.INFORMATION_MESSAGE)));
    }
}
