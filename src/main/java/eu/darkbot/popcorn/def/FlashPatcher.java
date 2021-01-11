package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.itf.Task;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.gui.utils.Popups;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Feature(name = "Flash patcher", description = "Sets flash configuration to continue running after jan 12th", enabledByDefault = true)
public class FlashPatcher implements Task {

    private static final Path
            FLASH_FOLDER = Paths.get(System.getenv("WINDIR"), "SysWOW64", "Macromed", "Flash"),
            FLASH_CONFIG = FLASH_FOLDER.resolve("mms.cfg"),
            TMP_SCRIPT = Paths.get("FlashPatcher.bat"),
            TMP_CONFIG = Paths.get("mms.cfg");

    private static final List<String> CONFIG_CONTENT = Arrays.asList(
                    "EnableAllowList=1",
                    "AllowListPreview=1",
                    "AllowListRootMovieOnly=1",
                    "AllowlistUrlPattern=https://*.bpsecure.com/",
                    "SilentAutoUpdateEnable=1",
                    "AutoUpdateDisable=1",
                    "EOLUninstallDisable=1");

    private static final List<String> TEMP_BATCH = Arrays.asList(
            "mkdir \"" + FLASH_FOLDER.toAbsolutePath().toString() + "\"",
            "move \"" + TMP_CONFIG.toAbsolutePath().toString() + "\" \"" + FLASH_CONFIG.toAbsolutePath().toString() + "\"",
            "del \"" + TMP_SCRIPT.toAbsolutePath().toString() + "\"");

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();

        if (Files.exists(FLASH_FOLDER) && Files.exists(FLASH_CONFIG)) {
            try {
                if (Files.readAllLines(FLASH_CONFIG).equals(CONFIG_CONTENT)) return;
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        Popups.showMessageSync("Flash patcher",
                new JOptionPane("Flash is blocked by default after 12/01/2021.\n" +
                        "Darkbot will need a one-time admin permission to patch flash and make it work.\n" +
                        "Accept on the next pop-up to run as admin to be able to continue using DarkBot.\n" +
                        "After the script runs, refresh the game or restart the bot to make it work.\n",
                        JOptionPane.INFORMATION_MESSAGE));

        try {
            Files.write(TMP_SCRIPT, TEMP_BATCH);
            Files.write(TMP_CONFIG, CONFIG_CONTENT);
            Runtime.getRuntime().exec("powershell start -verb runas './FlashPatcher.bat'").waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void tick() {}

}
