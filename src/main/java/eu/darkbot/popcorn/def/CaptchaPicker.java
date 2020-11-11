package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.FlashResManager;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.TemporalModule;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Feature(name = "Captcha picker", description = "Picks up captcha boxes when they appear", enabledByDefault = true)
public class CaptchaPicker extends TemporalModule {

    private Main main;
    private HeroManager hero;
    private Drive drive;

    private FlashResManager flashResManager;
    private final Consumer<String> logConsumer = this::onLogReceived;

    private Captcha boxMatch;
    private List<Box> boxes;

    @Override
    public void install(Main main) {
        super.install(main);

        this.main = main;
        this.hero = main.hero;
        this.drive = main.hero.drive;
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
                this.boxMatch = captcha;

                if (main.module != this) main.setModule(this);
            }
        }
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public String status() {
        return "Solving captcha" + (boxMatch != null ? " " + boxMatch : "");
    }

    @Override
    public void tick() {
        boxes.stream()
                .filter(box -> box.type.equals(boxMatch.name))
                .min(Comparator.comparingDouble(box -> hero.locationInfo.now.distance(box)))
                .ifPresent(box -> {
                    if (!boxMatch.hasAmount) {
                        boxMatch.amount = (int) boxes.stream()
                                .filter(b -> b.type.equals(boxMatch.name))
                                .count();
                        boxMatch.hasAmount = true;
                    }
                    collectBox(box);
                });
        if (boxMatch.amount == 0) goBack();
    }

    private void collectBox(Box box) {
        double distance = hero.locationInfo.distance(box);

        if (distance < 200) {
            drive.stop(false);
            box.clickable.setRadius(800);
            drive.clickCenter(true, box.locationInfo.now);

            box.setCollected();
            if (box.getRetries() > 0 && box.removed) boxMatch.amount--;
        } else {
            drive.move(box);
        }
    }

    private enum Captcha {
        SOME_BLACK("POISON_PUSAT_BOX_BLACK", "captcha_choose_some_black"),
        ALL_BLACK("POISON_PUSAT_BOX_BLACK", "captcha_choose_all_black"),
        SOME_RED("BONUS_BOX_RED", "captcha_choose_some_red"),
        ALL_RED("BONUS_BOX_RED", "captcha_choose_all_black");

        public final String name, key;
        private Pattern pattern;
        private boolean hasAmount;
        private int amount, time;

        Captcha(String name, String key) {
            this.name = name;
            this.key = key;
        }

        public boolean matches(String log, FlashResManager resManager) {
            if (pattern == null) {
                if (resManager == null) return false;
                String translation = resManager.getTranslation(key);
                if (translation == null || translation.isEmpty()) return false;

                this.hasAmount = translation.contains("%AMOUNT%");

                pattern = Pattern.compile(translation
                        .replaceAll("%AMOUNT%", "([0-9]+)")
                        .replaceAll("%TIME%", "(.*)"));
            }

            Matcher m = pattern.matcher(log);
            boolean matched = m.matches();
            if (hasAmount && matched) {
                amount = Integer.parseInt(m.group(1));
                time = Integer.parseInt(m.group(2).replaceAll("^[0-9]", ""));
            }
            return matched;
        }

        @Override
        public String toString() {
            return name() + (hasAmount ?  " " + amount : "");
        }
    }
}