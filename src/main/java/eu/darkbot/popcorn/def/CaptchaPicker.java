package eu.darkbot.popcorn.def;

import eu.darkbot.api.events.EventHandler;
import eu.darkbot.api.events.Listener;
import eu.darkbot.api.extensions.Behavior;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.game.entities.Box;
import eu.darkbot.api.game.other.Locatable;
import eu.darkbot.api.game.other.Location;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.BotAPI;
import eu.darkbot.api.managers.EntitiesAPI;
import eu.darkbot.api.managers.GameResourcesAPI;
import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.GameLogAPI.LogMessageEvent;
import eu.darkbot.api.managers.MovementAPI;
import eu.darkbot.api.managers.WindowAPI;
import eu.darkbot.shared.modules.TemporalModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Feature(name = "Captcha picker", description = "Picks up captcha boxes when they appear", enabledByDefault = true)
public class CaptchaPicker extends TemporalModule implements Behavior, Listener {

    private static final Pattern SPECIAL_REGEX = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    private static final Set<String> ALL_CAPTCHA_TYPES =
            Arrays.stream(Captcha.values()).map(c -> c.box).collect(Collectors.toSet());

    private final BotAPI bot;
    private final HeroAPI hero;
    private final MovementAPI movement;
    private final WindowAPI window;
    private final GameResourcesAPI gameResources;

    private final Collection<? extends Box> boxes;

    private final List<LogMessageEvent> pastLogMessages = new ArrayList<>();

    private Captcha captchaType;
    private List<Box> toCollect;
    private long waiting, maxActiveTime;

    public CaptchaPicker(BotAPI bot,
                         HeroAPI hero,
                         MovementAPI movement,
                         WindowAPI window,
                         GameResourcesAPI gameResources,
                         EntitiesAPI entities,
                         AuthAPI auth) {
        super(bot);
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) throw new SecurityException();
        VerifierChecker.checkAuthenticity(auth);

        this.bot = bot;
        this.hero = hero;
        this.movement = movement;
        this.window = window;
        this.gameResources = gameResources;
        this.boxes = entities.getBoxes();
    }

    @EventHandler
    public void onLogReceived(LogMessageEvent ev) {
        // Previous to flash resource manager initialization, translations may be null, if so, store messages.
        if (!gameResources.findTranslation(Captcha.SOME_RED.key).isPresent()) {
            pastLogMessages.add(ev);
            return;
        }

        for (Captcha captcha : Captcha.values()) {
            if (captcha.matches(ev.getMessage(), gameResources)) setCurrentCaptcha(captcha);
        }
    }

    @Override
    public boolean canRefresh() {
        return false;
    }

    @Override
    public String getStatus() {
        return "Solving captcha: Collecting " + (captchaType == null ? "(waiting for log...)" :
                (captchaType.hasAmount ? captchaType.amount : "all") + " " + captchaType.box + " box(es)");
    }

    @Override
    public void onTickBehavior() {
        // Translations finally loaded, process past message
        if (!pastLogMessages.isEmpty() && gameResources.findTranslation(Captcha.SOME_RED.key).isPresent()) {
            pastLogMessages.forEach(this::onLogReceived);
            pastLogMessages.clear();
        }

        // Set module to work if there's any
        if (bot.getModule() != this && hasAnyCaptchaBox()) {
            maxActiveTime = System.currentTimeMillis() + 30_000; // 30sec max to solve
            bot.setModule(this);
        }
    }

    @Override
    public void onTickModule() {
        if (isWaiting()) return;
        if (!hasAnyCaptchaBox()) goBack();

        movement.stop(false);

        if (System.currentTimeMillis() > maxActiveTime) {
            System.out.println("Triggering refresh: Timed out trying to solve captcha");
            goBack();
            window.handleRefresh();
        }

        if (toCollect == null) {
            if (captchaType == null || boxes.isEmpty()) return;

            List<? extends Box> filteredBoxes = boxes.stream().filter(captchaType::matches)
                    .collect(Collectors.toList());

            Location center = getCenter(filteredBoxes);
            double closestAngle = filteredBoxes.stream().min(Comparator.comparingDouble(hero::distanceTo))
                    .map(b -> angle(b, center)).orElse(0d);

            Stream<? extends Box> boxStream = filteredBoxes.stream()
                    .sorted(Comparator.comparingDouble(b -> {
                        double angle = this.angle(b, center);
                        return angle >= closestAngle ? angle : 10 - angle;
                    }));
            if (captchaType.hasAmount) boxStream = boxStream.limit(captchaType.amount);

            toCollect = boxStream.collect(Collectors.toList());
        }

        toCollect.stream().filter(b -> !b.isCollected())
                .findFirst().ifPresent(this::collectBox);
    }

    private Location getCenter(List<? extends Locatable> locations) {
        Location center = locations.stream().reduce(Location.of(0, 0), Location::plus, Location::plus);
        return center.setTo(center.getX() / locations.size(), center.getY() / locations.size());
    }

    private double angle(Locatable loc, Location center) {
        return Math.atan2(loc.getX() - center.getX(), loc.getY() - center.getY());
    }

    @Override
    public void onTickStopped() {
        // While paused or invalid, add 30s to solve
        maxActiveTime = System.currentTimeMillis() + 30_000;
    }

    private boolean hasAnyCaptchaBox() {
        return boxes.stream().map(Box::getTypeName).anyMatch(ALL_CAPTCHA_TYPES::contains);
    }

    private void collectBox(Box box) {
        box.tryCollect();

        waiting = System.currentTimeMillis()
                + Math.min(1_000, box.getRetries() * 100) // Add 100ms per retry, max 1 second
                + hero.timeTo(box) + 500;
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
    public void goBack() {
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

        public boolean matches(String log, GameResourcesAPI resManager) {
            if (pattern == null) {
                if (resManager == null) return false;
                String translation = resManager.findTranslation(key).orElse(null);
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
            return this.box.equals(box.getTypeName());
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