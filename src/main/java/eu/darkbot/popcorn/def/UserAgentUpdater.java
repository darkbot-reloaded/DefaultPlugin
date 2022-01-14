package eu.darkbot.popcorn.def;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Readonly;
import eu.darkbot.api.config.util.OptionEditor;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.Task;
import eu.darkbot.api.managers.AuthAPI;
import eu.darkbot.api.managers.ConfigAPI;
import eu.darkbot.util.http.Http;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Feature(name = "DO client updater", description = "Updates the DO client version darkbot pretends to use", enabledByDefault = true)
public class UserAgentUpdater implements Task, Configurable<UserAgentUpdater.Config> {

    private static final String URL = "http://darkorbit-22-client.bpsecure.com/bpflashclient/windows.x64/repository/Updates.xml";
    private static final Pattern VERSION = Pattern.compile("<version>(.*)</version>", Pattern.CASE_INSENSITIVE);

    private static final long HOUR = 3600 * 1000;

    private final ConfigAPI configAPI;

    private ConfigSetting<String> userAgent;
    private ConfigSetting<Long> nextUpdate;

    public UserAgentUpdater(ConfigAPI configAPI, AuthAPI auth) {
        if (!Arrays.equals(VerifierChecker.class.getSigners(), getClass().getSigners())) throw new SecurityException();
        VerifierChecker.checkAuthenticity(auth);

        this.configAPI = configAPI;
    }

    @Configuration("agent_updater.config")
    public static class Config {
        @Readonly
        public String USER_AGENT = Http.getDefaultUserAgent();
        @Editor(JTimeDisplay.class)
        public long NEXT_UPDATE = System.currentTimeMillis();
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.userAgent = configAPI.getConfig(config, "user_agent");
        this.nextUpdate = configAPI.getConfig(config, "next_update");

        Http.setDefaultUserAgent(userAgent.getValue());
    }

    @Override
    public void onTickTask() {
        if (System.currentTimeMillis() <= nextUpdate.getValue()) return;

        String version = Http.create(URL).consumeInputStream(in ->
                new BufferedReader(new InputStreamReader(in)).lines()
                        .map(VERSION::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(1))
                        .findFirst().orElse(null));

        if (version == null) nextUpdate.setValue(System.currentTimeMillis() + HOUR);
        else {
            userAgent.setValue("BigpointClient/" + version);
            Http.setDefaultUserAgent(userAgent.getValue());
            nextUpdate.setValue(System.currentTimeMillis() + (6 * HOUR));
        }
    }

    public static class JTimeDisplay extends TableOptimizedJLabel implements OptionEditor<Long> {
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

        private Long value;

        @Override
        public JComponent getEditorComponent(ConfigSetting<Long> time) {
            this.value = time.getValue();
            this.setText(Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(formatter));
            return this;
        }

        @Override
        public Long getEditorValue() {
            return value;
        }
    }


    static class TableOptimizedJLabel extends JLabel {
        /**
         * No-op methods improve performance when using this as a cell renderer, and they are not needed anyways.
         */
        public void validate() {}
        public void invalidate() {}
        public void revalidate() {}
        public void repaint(long tm, int x, int y, int width, int height) {}
        public void repaint(Rectangle r) {}
        public void repaint() {}
        public void firePropertyChange(String propertyName, byte oldValue, byte newValue) {}
        public void firePropertyChange(String propertyName, char oldValue, char newValue) {}
        public void firePropertyChange(String propertyName, short oldValue, short newValue) {}
        public void firePropertyChange(String propertyName, int oldValue, int newValue) {}
        public void firePropertyChange(String propertyName, long oldValue, long newValue) {}
        public void firePropertyChange(String propertyName, float oldValue, float newValue) {}
        public void firePropertyChange(String propertyName, double oldValue, double newValue) {}
        public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
    }

}
