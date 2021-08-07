package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.modules.utils.LegacyFlashPatcher;
import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Installable;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.AuthAPI;

import java.util.Arrays;

@Feature(name = "Flash patcher", description = "Installs flash to work again after removal", enabledByDefault = true)
public class FlashPatcher extends LegacyFlashPatcher implements Task, Installable {

    @Override
    public void install(PluginAPI api) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity(api.requireAPI(AuthAPI.class));

        super.runPatcher();
    }

    @Override
    public void uninstall() {
    }

    @Override
    public void onTickTask() {}

}
