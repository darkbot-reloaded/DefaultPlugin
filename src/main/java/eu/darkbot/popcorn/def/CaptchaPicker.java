package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.backpage.FlashResManager;
import com.github.manolo8.darkbot.core.entities.Box;
import com.github.manolo8.darkbot.core.itf.Behaviour;
import com.github.manolo8.darkbot.core.manager.HeroManager;
import com.github.manolo8.darkbot.core.utils.Drive;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.modules.TemporalModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Feature(name = "Captcha picker", description = "Picks up captcha boxes when they appear", enabledByDefault = true)
public class CaptchaPicker extends TemporalModule implements Behaviour {

    private static final Pattern SPECIAL_REGEX = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    private static final Set<String> ALL_CAPTCHA_TYPES =
            Arrays.stream(Captcha.values()).map(c -> c.box).collect(Collectors.toSet());

    private Main main;
    private HeroManager hero;
    private Drive drive;

    private FlashResManager flashResManager;
    private final Consumer<String> logConsumer = this::onLogReceived;
    private final List<String> pastLogMessages = new ArrayList<>();

    private Captcha captchaType;
    private List<Box> boxes, toCollect;
    private long waiting, maxActiveTime;

    @Override
    public void install(Main main) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();
        super.install(main);

        this.main = main;
        this.hero = main.hero;
        this.drive = main.hero.drive;
        this.flashResManager = main.featureRegistry.getFeature(FlashResManager.class)
                .orElseThrow(IllegalStateException::new);
        this.boxes = main.mapManager.entities.boxes;

        this.toCollect = null;

        main.facadeManager.log.logs.add(logConsumer);
    }

    @Override
    public void uninstall() {
        main.facadeManager.log.logs.remove2(logConsumer);
    }

    private void onLogReceived(String log) {
        // Previous to flash resource manager initialization, translations may be null, if so, store messages.
        if (flashResManager.getTranslation(Captcha.SOME_RED.key) == null) {
            pastLogMessages.add(log);
            return;
        }

        for (Captcha captcha : Captcha.values()) {
            if (captcha.matches(log, flashResManager)) setCurrentCaptcha(captcha);
        }
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public String status() {
        return "Solving captcha: Collecting " + (captchaType == null ? "(waiting for log...)" :
                (captchaType.hasAmount ? captchaType.amount : "all") + " " + captchaType.box + " box(es)");
    }

    @Override
    public void tickBehaviour() {
        // Translations finally loaded, process past message
        if (!pastLogMessages.isEmpty() && flashResManager.getTranslation(Captcha.SOME_RED.key) != null) {
            pastLogMessages.forEach(this::onLogReceived);
            pastLogMessages.clear();
        }

        // Set module to work if there's any
        if (main.module != this && hasAnyCaptchaBox()) {
            maxActiveTime = System.currentTimeMillis() + 30_000; // 30sec max to solve
            main.setModule(this);
        }
    }

    @Override
    public void tick() {
        if (isWaiting()) return;
        if (!hasAnyCaptchaBox()) goBack();

        drive.stop(false);

        if (System.currentTimeMillis() > maxActiveTime) {
            System.out.println("Triggering refresh: Timed out trying to solve captcha");
            goBack();
            Main.API.handleRefresh();
        }

        if (toCollect == null) {
            if (captchaType == null) return;

            Stream<Box> boxStream = boxes.stream()
                    .filter(captchaType::matches)
                    .sorted(Comparator.comparingDouble(box -> hero.locationInfo.now.distance(box)));
            if (captchaType.hasAmount) boxStream = boxStream.limit(captchaType.amount);

            toCollect = boxStream.collect(Collectors.toList());
        }

        toCollect.stream().filter(b -> !b.isCollected())
                .findFirst().ifPresent(this::collectBox);
    }

    @Override
    public void tickStopped() {
        // While paused or invalid, add 30s to solve
        maxActiveTime = System.currentTimeMillis() + 30_000;
    }

    private boolean hasAnyCaptchaBox() {
        return boxes.stream().map(b -> b.type)
                .anyMatch(ALL_CAPTCHA_TYPES::contains);
    }

    private void collectBox(Box box) {
        box.clickable.setRadius(800);
        drive.clickCenter(true, box.locationInfo.now);

        box.setCollected();
        waiting = System.currentTimeMillis()
                + Math.min(1_000, box.getRetries() * 100) // Add 100ms per retry, max 1 second
                + hero.timeTo(hero.locationInfo.distance(box)) + 500;
    }

    public boolean isWaiting() {
        return System.currentTimeMillis() < waiting;
    }

    private void setCurrentCaptcha(Captcha captcha) {
        waiting = System.currentTimeMillis() + 500; // Wait for everything to be ready
        captchaType = captcha;
        toCollect = null;
    }

    @Override
    protected void goBack() {
        this.toCollect = null; // Make sure we let them GC

        super.goBack();
    }

    private enum Captcha {
        SOME_BLACK("POISON_PUSAT_BOX_BLACK", "captcha_choose_some_black"),
        ALL_BLACK("POISON_PUSAT_BOX_BLACK", "captcha_choose_all_black"),
        SOME_RED("BONUS_BOX_RED", "captcha_choose_some_red"),
        ALL_RED("BONUS_BOX_RED", "captcha_choose_all_red");

        private final String box, key;
        private Pattern pattern;
        private boolean hasAmount;
        private int amount, time;

        Captcha(String name, String key) {
            this.box = name;
            this.key = key;
        }

        public boolean matches(String log, FlashResManager resManager) {
            if (pattern == null) {
                if (resManager == null) return false;
                String translation = resManager.getTranslation(key);
                if (translation == null || translation.isEmpty()) return false;

                this.hasAmount = translation.contains("%AMOUNT%");

                pattern = Pattern.compile(escapeRegex(translation)
                        .replace("%AMOUNT%", "(?<amount>[0-9]+)")
                        .replace("%TIME%", "(?<time>[0-9]+)"));
            }

            Matcher m = pattern.matcher(log);
            boolean matched = m.matches();
            if (hasAmount && matched) {
                amount = Integer.parseInt(m.group("amount"));
                time = Integer.parseInt(m.group("time"));
            }
            return matched;
        }

        public boolean matches(Box box) {
            return this.box.equals(box.type);
        }

        @Override
        public String toString() {
            return name() + (hasAmount ?  " " + amount : "");
        }
    }

    private static String escapeRegex(String str) {
        return SPECIAL_REGEX.matcher(str).replaceAll("\\\\$0");
    }

}