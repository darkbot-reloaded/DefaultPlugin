package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.FlashResManager;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.TemporalModule;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Feature(name = "Captcha picker", description = "Picks up captcha boxes when they appear", enabledByDefault = true)
public class CaptchaPicker extends TemporalModule {

    private Main main;

    private enum Captcha {
        SOME_BLACK("captcha_chose_some_black"),
        ALL_BLACK("captcha_chose_all_black"),
        SOME_RED("captcha_chose_some_red"),
        ALL_RED("captcha_chose_all_black");

        public final String key;
        private Pattern pattern;
        private boolean hasAmount;
        private int amount;

        Captcha(String key) {
            this.key = key;
        }

        public boolean matches(String log, FlashResManager resManager) {
            if (pattern == null) {
                if (resManager == null) return false;
                String translation = resManager.getTranslation(key);
                if (translation == null || translation.isEmpty()) return false;

                this.hasAmount = translation.contains("%AMOUNT%");

                pattern = Pattern.compile(translation.replaceAll("%AMOUNT%", "([0-9]+)").replaceAll("%TIME%", ".*"));
            }

            Matcher m = pattern.matcher(log);
            boolean matched = m.matches();
            if (hasAmount && matched) amount = Integer.parseInt(m.group(1));
            return matched;
        }

        public int getAmount() {
            return hasAmount ? -1 : amount;
        }

        @Override
        public String toString() {
            return name() + (hasAmount ?  " " + amount : "");
        }
    }

    private FlashResManager flashResManager;
    private final Consumer<String> logConsumer = this::onLogReceived;

    private Captcha currentMatch;

    private List<Box> boxes;

    public CaptchaPicker() {}

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();

        this.main = main;
        this.flashResManager = main.featureRegistry.getFeature(FlashResManager.class)
                .orElseThrow(IllegalStateException::new);
        this.boxes = main.mapManager.entities.boxes;

        main.facadeManager.log.logs.add(logConsumer);
    }

    @Override
    public void uninstall() {
        main.facadeManager.log.logs.remove2(logConsumer);
    }

    private void onLogReceived(String log) {
        for (Captcha captcha : Captcha.values()) {
            if (captcha.matches(log, flashResManager)) {
                this.currentMatch = captcha;

                if (main.module != this) main.setModule(this);
            }
        }
        this.currentMatch = null;
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public String status() {
        return "Solving captcha" + (currentMatch != null ? " " + currentMatch : "");
    }

    @Override
    public void tick() {


    }

}
