package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.itf.Task;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.utils.LegacyFlashPatcher;

import java.util.Arrays;

@Feature(name = "Flash patcher", description = "Installs flash to work again after removal", enabledByDefault = true)
public class FlashPatcher extends LegacyFlashPatcher implements Task {

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();

        super.runPatcher();
    }

    @Override
    public void tick() {}

}
