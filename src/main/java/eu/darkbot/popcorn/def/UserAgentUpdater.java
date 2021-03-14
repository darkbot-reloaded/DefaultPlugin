package eu.darkbot.popcorn.def;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.ConfigEntity;
import com.github.manolo8.darkbot.config.tree.ConfigField;
import com.github.manolo8.darkbot.config.types.Editor;
import com.github.manolo8.darkbot.config.types.Length;
import com.github.manolo8.darkbot.config.types.Option;
import com.github.manolo8.darkbot.core.itf.Configurable;
import com.github.manolo8.darkbot.core.itf.Task;
import com.github.manolo8.darkbot.extensions.features.Feature;
import com.github.manolo8.darkbot.gui.tree.OptionEditor;
import com.github.manolo8.darkbot.gui.tree.components.JLabelField;
import com.github.manolo8.darkbot.utils.Time;
import com.github.manolo8.darkbot.utils.http.Http;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Feature(name = "DO client updater", description = "Updates the DO client version darkbot pretends to use", enabledByDefault = true)
public class UserAgentUpdater implements Task, Configurable<UserAgentUpdater.Config> {

    private static final String URL = "http://darkorbit-22-client.bpsecure.com/bpflashclient/windows.x64/repository/Updates.xml";
    private static final Pattern VERSION = Pattern.compile("<version>(.*)</version>", Pattern.CASE_INSENSITIVE);

    private Config config;

    public static class Config {
        @Option("User agent (auto-updated)")
        @Editor(JLabelField.class)
        public String USER_AGENT = Http.getDefaultUserAgent();
        @Option("Next update")
        @Editor(JTimeDisplay.class)
        public long NEXT_UPDATE = System.currentTimeMillis();
    }

    @Override
    public void setConfig(Config config) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) return;
        VerifierChecker.checkAuthenticity();

        this.config = config;
        Http.setDefaultUserAgent(config.USER_AGENT);
    }

    @Override
    public void install(Main main) {
    }

    @Override
    public void tick() {
        if (System.currentTimeMillis() <= config.NEXT_UPDATE) return;

        String version = Http.create(URL).consumeInputStream(in ->
                new BufferedReader(new InputStreamReader(in)).lines()
                        .map(VERSION::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1))
                        .findFirst().orElse(null));

        if (version == null) config.NEXT_UPDATE = System.currentTimeMillis() + Time.HOUR;
        else {
            config.USER_AGENT = "BigpointClient/" + version;
            Http.setDefaultUserAgent(config.USER_AGENT);
            config.NEXT_UPDATE = System.currentTimeMillis() + (6 * Time.HOUR);
        }
        ConfigEntity.changed();
    }

    public static class JTimeDisplay extends JLabelField {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

        @Override
        public JComponent getComponent() {
            return this;
        }

        @Override
        public void edit(ConfigField field) {
            long time = field.get();
            this.setText(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(formatter));
        }
    }

}
