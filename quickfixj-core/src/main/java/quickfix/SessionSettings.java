/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved.
 *
 * This file is part of the QuickFIX FIX Engine
 *
 * This file may be distributed under the terms of the quickfixengine.org
 * license as defined by quickfixengine.org and appearing in the file
 * LICENSE included in the packaging of this file.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE.
 *
 * See http://www.quickfixengine.org/LICENSE for licensing information.
 *
 * Contact ask@quickfixengine.org if any conditions of this licensing
 * are not clear to you.
 ******************************************************************************/

package quickfix;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.field.converter.BooleanConverter;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Settings for sessions. Settings are grouped by FIX version and target company
 * ID. There is also a default settings section that is inherited by the
 * session-specific sections.
 *
 * Setting constants are declared in the classes using the settings. To find the
 * string constants, navigate to the class constant for the setting, select the
 * link for the setting and then and select the "Constant Field Values" link in
 * the detailed field description.
 *
 * @see quickfix.Acceptor
 * @see quickfix.Initiator
 * @see quickfix.FileLogFactory
 * @see quickfix.ScreenLogFactory
 * @see quickfix.FileStoreFactory
 * @see quickfix.JdbcSetting
 * @see quickfix.Session
 * @see quickfix.DefaultSessionFactory
 */
public class SessionSettings {
    private static final Logger log = LoggerFactory.getLogger(SessionSettings.class);

    private static final SessionID DEFAULT_SESSION_ID = new SessionID("DEFAULT", "", "");
    private static final String SESSION_SECTION_NAME = "session";
    private static final String DEFAULT_SECTION_NAME = "default";
    public static final String BEGINSTRING = "BeginString";
    public static final String SENDERCOMPID = "SenderCompID";
    public static final String SENDERSUBID = "SenderSubID";
    public static final String SENDERLOCID = "SenderLocationID";
    public static final String TARGETCOMPID = "TargetCompID";
    public static final String TARGETSUBID = "TargetSubID";
    public static final String TARGETLOCID = "TargetLocationID";
    public static final String SESSION_QUALIFIER = "SessionQualifier";

    // This was using the line.separator system property but that caused
    // problems with moving configuration files between *nix and Windows.
    private static final String NEWLINE = "\r\n";

    private Properties variableValues;

    /**
     * Creates an empty session settings object.
     */
    public SessionSettings() {
        this(System.getProperties());
    }

    /**
     * Creates an empty session settings object with custom source of variable values in the settings.
     * @param variableValues custom source of variable values in the settings
     */
    public SessionSettings(Properties variableValues) {
        sections.put(DEFAULT_SESSION_ID, new Properties());
        this.variableValues = variableValues;
    }

    /**
     * Loads session settings from a file with custom source of variable values in the settings.
     *
     * @param filename the path to the file containing the session settings
     * @param variableValues custom source of variable values in the settings
     * @throws ConfigError when file could not be loaded
     */
    public SessionSettings(String filename, Properties variableValues) throws ConfigError {
        this(variableValues);
        loadFromFile(filename);
    }

    /**
     * Loads session settings from a file.
     *
     * @param filename the path to the file containing the session settings
     * @throws ConfigError when file could not be loaded
     */
    public SessionSettings(String filename) throws ConfigError {
        this();
        loadFromFile(filename);
    }

    /**
     * Loads session settings from an input stream.
     *
     * @param stream the input stream
     * @throws ConfigError
     */
    public SessionSettings(InputStream stream) throws ConfigError {
        this();
        load(stream);
    }

    /**
     * Loads session settings from an input stream with custom source of variable values in the settings.
     *
     * @param stream the input stream
     * @param variableValues custom source of variable values in the settings
     * @throws ConfigError
     */
    public SessionSettings(InputStream stream, Properties variableValues) throws ConfigError {
        this(variableValues);
        load(stream);
    }

    /**
     * Loads session settings from a list of strings.
     *
     * @param listValues the list of strings
     * @throws ConfigError
     */
    public SessionSettings(List<String> listValues) throws ConfigError {
        this();
        loadFromList(listValues);
    }

    /**
     * Loads session settings from a list of strings with custom source of variable values in the settings.
     *
     * @param listValues the list of strings
     * @param variableValues custom source of variable values in the settings
     * @throws ConfigError
     */
    public SessionSettings(List<String> listValues, Properties variableValues) throws ConfigError {
        this(variableValues);
        loadFromList(listValues);
    }

    /**
     * Gets a string from the default section of the settings.
     *
     * @param key
     * @return the default string value
     * @throws ConfigError
     */
    public String getString(String key) throws ConfigError {
        return getString(DEFAULT_SESSION_ID, key);
    }

    /**
     * Gets a string from the default section if present or use default value.
     */
    public String getStringOrDefault(String key, String defaultValue) throws ConfigError {
        return isSetting(key) ? getString(key) : defaultValue;
    }

    /**
     * Get a settings string.
     *
     * @param sessionID the session ID
     * @param key the settings key
     * @return the string value for the setting
     * @throws ConfigError configuration error, probably a missing setting.
     */
    public String getString(SessionID sessionID, String key) throws ConfigError {
        final String value = interpolate(getSessionProperties(sessionID).getProperty(key));
        if (value == null) {
            throw new ConfigError(key + " not defined");
        }
        return value;
    }

    /**
     * Get a settings string if present or use default value.
     */
    public String getStringOrDefault(SessionID sessionID, String key, String defaultValue) throws ConfigError {
        return isSetting(sessionID, key) ? getString(sessionID, key) : defaultValue;
    }

    /**
     * Return the settings for a session as a Properties object.
     *
     * @param sessionID
     * @param includeDefaults if true, include settings defaults in properties
     * @return the Properties object with the session settings
     * @throws ConfigError
     * @see java.util.Properties
     */
    public Properties getSessionProperties(SessionID sessionID, boolean includeDefaults)
            throws ConfigError {
        final Properties p = sections.get(sessionID);
        if (p == null) {
            throw new ConfigError("Session not found");
        }
        if (includeDefaults) {
            final Properties mergedProperties = new Properties();
            mergedProperties.putAll(sections.get(DEFAULT_SESSION_ID));
            mergedProperties.putAll(p);
            return mergedProperties;
        } else {
            return p;
        }
    }

    /**
     * Return the settings for a session as a Properties object.
     *
     * @param sessionID
     * @return the Properties object with the session settings
     * @throws ConfigError
     * @see java.util.Properties
     */
    public Properties getSessionProperties(SessionID sessionID) throws ConfigError {
        return getSessionProperties(sessionID, false);
    }

    /**
     * Returns the defaults for the session-level settings.
     *
     * @return the default properties
     */
    public Properties getDefaultProperties() {
        try {
            return getSessionProperties(DEFAULT_SESSION_ID);
        } catch (final ConfigError e) {
            // shouldn't happen
            return new Properties();
        }
    }

    /**
     * Gets a long from the default section of settings.
     *
     * @param key
     * @return the default value
     * @throws ConfigError
     * @throws FieldConvertError
     */
    public long getLong(String key) throws ConfigError, FieldConvertError {
        return getLong(DEFAULT_SESSION_ID, key);
    }

    /**
     * Gets a long from the default section of settings if present or use default value.
     */
    public long getLongOrDefault(String key, long defaultValue) throws ConfigError, FieldConvertError {
        return isSetting(key) ? getLong(key) : defaultValue;
    }

    /**
     * Get a settings value as a long integer.
     *
     * @param sessionID the session ID
     * @param key the settings key
     * @return the long integer value for the setting
     * @throws ConfigError configuration error, probably a missing setting.
     * @throws FieldConvertError error during field type conversion.
     */
    public long getLong(SessionID sessionID, String key) throws ConfigError, FieldConvertError {
        try {
            return Long.parseLong(getString(sessionID, key));
        } catch (final NumberFormatException e) {
            throw new FieldConvertError(e.getMessage());
        }
    }

    /**
     * Get an existing settings value as a long if present or use default value.
     */
    public long getLongOrDefault(SessionID sessionID, String key, long defaultValue) throws ConfigError, FieldConvertError {
        return isSetting(sessionID, key) ? getLong(sessionID, key) : defaultValue;
    }

    /**
     * Gets an int from the default section of settings.
     *
     * @param key
     * @return the default value
     * @throws ConfigError
     * @throws FieldConvertError
     */
    public int getInt(String key) throws ConfigError, FieldConvertError {
        return getInt(DEFAULT_SESSION_ID, key);
    }

    /**
     * Gets an int from the default section of settings if present or use default value.
     */
    public int getIntOrDefault(String key, int defaultValue) throws ConfigError, FieldConvertError {
        return isSetting(key) ? getInt(key) : defaultValue;
    }

    /**
     * Get a settings value as an integer.
     *
     * @param sessionID the session ID
     * @param key       the settings key
     * @return the long integer value for the setting
     * @throws ConfigError       configuration error, probably a missing setting.
     * @throws FieldConvertError error during field type conversion.
     */
    public int getInt(SessionID sessionID, String key) throws ConfigError, FieldConvertError {
        try {
            return Integer.parseInt(getString(sessionID, key));
        } catch (final NumberFormatException e) {
            throw new FieldConvertError(e.getMessage());
        }
    }

    /**
     * Get an existing settings value as an integer if present or use default value.
     */
    public int getIntOrDefault(SessionID sessionID, String key, int defaultValue) throws ConfigError, FieldConvertError {
        return isSetting(sessionID, key) ? getInt(sessionID, key) : defaultValue;
    }

    private Properties getOrCreateSessionProperties(SessionID sessionID) {
        return sections.computeIfAbsent(sessionID, k -> new Properties(sections.get(DEFAULT_SESSION_ID)));
    }

    /**
     * Gets a double value from the default section of the settings.
     *
     * @param key
     * @return the default value
     * @throws ConfigError
     * @throws FieldConvertError
     */
    public double getDouble(String key) throws ConfigError, FieldConvertError {
        return getDouble(DEFAULT_SESSION_ID, key);
    }

    /**
     * Get a settings value as a double number.
     *
     * @param sessionID the session ID
     * @param key the settings key
     * @return the double number value for the setting
     * @throws ConfigError configuration error, probably a missing setting.
     * @throws FieldConvertError error during field type conversion.
     */
    public double getDouble(SessionID sessionID, String key) throws ConfigError, FieldConvertError {
        try {
            return Double.parseDouble(getString(sessionID, key));
        } catch (final NumberFormatException e) {
            throw new FieldConvertError(e.getMessage());
        }
    }

    /**
     * Gets a boolean value from the default section of the settings.
     *
     * @param key
     * @return the boolean value
     * @throws ConfigError
     * @throws FieldConvertError
     */
    public boolean getBool(String key) throws ConfigError, FieldConvertError {
        return getBool(DEFAULT_SESSION_ID, key);
    }

    /**
     * Get a settings value as a boolean value.
     *
     * @param sessionID the session ID
     * @param key the settings key
     * @return the boolean value for the setting
     * @throws ConfigError configuration error, probably a missing setting.
     * @throws FieldConvertError error during field type conversion.
     */
    public boolean getBool(SessionID sessionID, String key) throws FieldConvertError, ConfigError {
        return BooleanConverter.convert(getString(sessionID, key));
    }

    /**
     * Sets a string-valued session setting.
     *
     * @param sessionID the session ID
     * @param key the setting key
     * @param value the string value
     */
    public void setString(SessionID sessionID, String key, String value) {
        getOrCreateSessionProperties(sessionID).setProperty(key, value.trim());
    }

    /**
     * Sets a long integer-valued session setting.
     *
     * @param sessionID the session ID
     * @param key the setting key
     * @param value the long integer value
     */
    public void setLong(SessionID sessionID, String key, long value) {
        getOrCreateSessionProperties(sessionID).setProperty(key, Long.toString(value));
    }

    /**
     * Sets a double-valued session setting.
     *
     * @param sessionID the session ID
     * @param key the setting key
     * @param value the double value
     */
    public void setDouble(SessionID sessionID, String key, double value) {
        getOrCreateSessionProperties(sessionID).setProperty(key, Double.toString(value));
    }

    /**
     * Sets a boolean-valued session setting.
     *
     * @param sessionID the session ID
     * @param key the setting key
     * @param value the boolean value
     */
    public void setBool(SessionID sessionID, String key, boolean value) {
        getOrCreateSessionProperties(sessionID).setProperty(key, BooleanConverter.convert(value));
    }

    private final ConcurrentMap<SessionID, Properties> sections = new ConcurrentHashMap<>();

    public Iterator<SessionID> sectionIterator() {
        final HashSet<SessionID> nondefaultSessions = new HashSet<>(sections.keySet());
        nondefaultSessions.remove(DEFAULT_SESSION_ID);
        return nondefaultSessions.iterator();
    }

    private void loadFromFile(String filename) throws ConfigError {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (in != null) {
                load(in);
            } else {
                try (InputStream in2 = new FileInputStream(filename)) {
                    load(in2);
                }
            }
        } catch (final IOException ex) {
            throw new ConfigError(ex.getMessage());
        }
    }

    private void loadFromList(List<String> listValues) throws ConfigError {
        byte[] bytes = listValues.stream().collect(Collectors.joining(System.lineSeparator())).getBytes();
        InputStream in = new ByteArrayInputStream(bytes);
        load(in);
    }

    private void load(InputStream inputStream) throws ConfigError {
        try (final Reader reader = new InputStreamReader(inputStream)) {
            Properties currentSection = null;
            String currentSectionId = null;
            final Tokenizer tokenizer = new Tokenizer();
            Tokenizer.Token token = tokenizer.getToken(reader);
            while (token != null) {
                if (token.getType() == Tokenizer.SECTION_TOKEN) {
                    storeSection(currentSectionId, currentSection);
                    if (token.getValue().equalsIgnoreCase(DEFAULT_SECTION_NAME)) {
                        currentSectionId = DEFAULT_SECTION_NAME;
                        currentSection = getSessionProperties(DEFAULT_SESSION_ID);
                    } else if (token.getValue().equalsIgnoreCase(SESSION_SECTION_NAME)) {
                        currentSectionId = SESSION_SECTION_NAME;
                        currentSection = new Properties(getSessionProperties(DEFAULT_SESSION_ID));
                    }
                } else if (token.getType() == Tokenizer.ID_TOKEN) {
                    final Tokenizer.Token valueToken = tokenizer.getToken(reader);
                    if (currentSection != null) {
                        final String value = interpolate(valueToken.getValue());
                        currentSection.put(token.getValue(), value);
                    }
                }
                token = tokenizer.getToken(reader);
            }
            storeSection(currentSectionId, currentSection);
        } catch (final IOException e) {
            throw new ConfigError(e.getMessage());
        }
    }

    private void storeSection(String currentSectionId, Properties currentSection) {
        if (currentSectionId != null && currentSectionId.equals(SESSION_SECTION_NAME)) {
            final SessionID sessionId = new SessionID(currentSection.getProperty(BEGINSTRING),
                    currentSection.getProperty(SENDERCOMPID),
                    currentSection.getProperty(SENDERSUBID),
                    currentSection.getProperty(SENDERLOCID),
                    currentSection.getProperty(TARGETCOMPID),
                    currentSection.getProperty(TARGETSUBID),
                    currentSection.getProperty(TARGETLOCID),
                    currentSection.getProperty(SESSION_QUALIFIER));
            sections.put(sessionId, currentSection);
        }
    }

    /**
     * Predicate for determining if a setting is in the default section.
     *
     * @param key
     * @return true if setting is in the defaults, false otherwise
     */
    public boolean isSetting(String key) {
        return isSetting(DEFAULT_SESSION_ID, key);
    }

    /**
     * Predicate for determining if a setting exists.
     *
     * @param sessionID the session ID
     * @param key the setting key
     * @return true is setting exists, false otherwise.
     */
    public boolean isSetting(SessionID sessionID, String key) {
        return getOrCreateSessionProperties(sessionID).getProperty(key) != null;
    }

    public void removeSetting(SessionID sessionID, String key) {
        getOrCreateSessionProperties(sessionID).remove(key);
    }

    private static class Tokenizer {
        //public static final int NONE_TOKEN = 1;

        public static final int ID_TOKEN = 2;

        public static final int VALUE_TOKEN = 3;

        public static final int SECTION_TOKEN = 4;

        private static class Token {
            private final int type;

            private final String value;

            public Token(int type, String value) {
                super();
                this.type = type;
                this.value = value;
            }

            public int getType() {
                return type;
            }

            public String getValue() {
                return value;
            }

            @Override
            public String toString() {
                return type + ": " + value;
            }
        }

        private char ch = '\0';

        private final StringBuilder sb = new StringBuilder();

        private Token getToken(Reader reader) throws IOException {
            if (ch == '\0') {
                ch = nextCharacter(reader);
            }
            skipWhitespace(reader);
            if (isLabelCharacter(ch)) {
                sb.setLength(0);
                do {
                    sb.append(ch);
                    ch = nextCharacter(reader);
                } while (isLabelCharacter(ch));
                return new Token(ID_TOKEN, sb.toString());
            } else if (ch == '=') {
                ch = nextCharacter(reader);
                sb.setLength(0);
                if (isValueCharacter(ch)) {
                    do {
                        sb.append(ch);
                        ch = nextCharacter(reader);
                    } while (isValueCharacter(ch));
                }
                return new Token(VALUE_TOKEN, sb.toString().trim());
            } else if (ch == '[') {
                ch = nextCharacter(reader);
                final Token id = getToken(reader);
                // check ]
                ch = nextCharacter(reader); // skip ]
                return new Token(SECTION_TOKEN, id.getValue());
            } else if (ch == '#') {
                do {
                    ch = nextCharacter(reader);
                } while (isValueCharacter(ch));
                return getToken(reader);
            }
            return null;
        }

        private boolean isNewLineCharacter(char ch) {
            return NEWLINE.indexOf(ch) != -1;
        }

        private boolean isLabelCharacter(char ch) {
            return !isEndOfStream(ch) && "[]=#".indexOf(ch) == -1;
        }

        private boolean isValueCharacter(char ch) {
            return !isEndOfStream(ch) && !isNewLineCharacter(ch);
        }

        private boolean isEndOfStream(char ch) {
            return (byte) ch == -1;
        }

        private char nextCharacter(Reader reader) throws IOException {
            return (char) reader.read();
        }

        private void skipWhitespace(Reader reader) throws IOException {
            if (Character.isWhitespace(ch)) {
                do {
                    ch = nextCharacter(reader);
                } while (Character.isWhitespace(ch));
            }
        }
    }

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{(.+?)}");

    private String interpolate(String value) {
        if (value == null || value.indexOf('$') == -1) {
            return value;
        }
        final StringBuffer buffer = new StringBuffer();
        final Matcher m = VARIABLE_PATTERN.matcher(value);
        while (m.find()) {
            if (m.start() > 0 && value.charAt(m.start() - 1) == '\\') {
                continue;
            }
            final String variable = m.group(1);
            final String variableValue = variableValues.getProperty(variable);
            if (variableValue != null) {
                m.appendReplacement(buffer, variableValue);
            }
        }
        m.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Set properties that will be the source of variable values in the settings. A variable
     * is of the form ${variable} and will be replaced with values from the
     * map when the setting is retrieved.
     *
     * By default, the System properties are used for variable values. If
     * you use this method, it will override the defaults so use the Properties
     * default value mechanism if you want to chain a custom properties object
     * with System properties as the default.
     *
     * <code><pre>
     * // Custom properties with System properties as default
     * Properties myprops = new Properties(System.getProperties());
     * myprops.load(getPropertiesInputStream());
     * settings.setVariableValues(myprops);
     *
     * // Custom properties with System properties as override
     * Properties myprops = new Properties();
     * myprops.load(getPropertiesInputStream());
     * myprops.putAll(System.getProperties());
     * settings.setVariableValues(myprops);
     * </pre></code>
     *
     * @param variableValues
     *
     * @see java.util.Properties
     * @see java.lang.System
     */
    public void setVariableValues(Properties variableValues) {
        this.variableValues = variableValues;
    }

    /**
     * Adds defaults to the settings. Will not delete existing settings not
     * overlapping with the new defaults, but will overwrite existing settings
     * specified in this call.
     *
     * @param defaults
     */
    public void set(Map<Object, Object> defaults) {
        getOrCreateSessionProperties(DEFAULT_SESSION_ID).putAll(defaults);
    }

    /**
     * Set a default boolean parameter.
     *
     * @param key the settings key
     * @param value the settings value
     */
    public void setBool(String key, boolean value) {
        setBool(DEFAULT_SESSION_ID, key, value);
    }

    /**
     * Set a default double parameter.
     *
     * @param key the settings key
     * @param value the settings value
     */
    public void setDouble(String key, double value) {
        setDouble(DEFAULT_SESSION_ID, key, value);
    }

    /**
     * Set a default long parameter.
     *
     * @param key the settings key
     * @param value the settings value
     */
    public void setLong(String key, long value) {
        setLong(DEFAULT_SESSION_ID, key, value);
    }

    /**
     * Set a default string parameter.
     *
     * @param key the settings key
     * @param value the settings value
     */
    public void setString(String key, String value) {
        setString(DEFAULT_SESSION_ID, key, value.trim());
    }

    public int size() {
        // Always a default section
        return sections.size() - 1;
    }

    public Dictionary get(SessionID sessionID) throws ConfigError {
        return new Dictionary(null, getSessionProperties(sessionID));
    }

    public void set(SessionID sessionID, Dictionary dictionary) throws ConfigError {
        final Properties p = getOrCreateSessionProperties(sessionID);
        p.clear();
        p.putAll(dictionary.toMap());
    }

    public Dictionary get() {
        return new Dictionary(null, getDefaultProperties());
    }

    public void set(Dictionary dictionary) throws ConfigError {
        getDefaultProperties().putAll(dictionary.toMap());
    }

    public void toString(PrintWriter writer) {
        try {
            writeSection("[DEFAULT]", writer, getDefaultProperties());
            final Iterator<SessionID> s = sectionIterator();
            while (s.hasNext()) {
                try {
                    writeSection("[SESSION]", writer, getSessionProperties(s.next()));
                } catch (final ConfigError e) {
                    log.error("Invalid session", e);
                }
            }
        } finally {
            writer.flush();
        }
    }

    public void toStream(OutputStream out) {
        toString(new PrintWriter(new OutputStreamWriter(out)));
    }

    private void writeSection(String sectionName, PrintWriter writer, Properties properties) {
        writer.println(sectionName);
        for (Object o : properties.keySet()) {
            final String key = (String) o;
            writer.print(key);
            writer.print("=");
            writer.println(properties.getProperty(key));
        }
    }

    @Override
    public String toString() {
        final StringWriter writer = new StringWriter();
        toString(new PrintWriter(writer));
        return writer.toString();
    }

    public static int[] parseSettingReconnectInterval(String raw) {
        if (raw == null || raw.length() == 0) {
            return null;
        }
        final String multiplierCharacter = raw.contains("*") ? "\\*" : "x";
        final String[] data = raw.split(";");
        final List<Integer> result = new ArrayList<>();
        for (final String multi : data) {
            final String[] timesSec = multi.split(multiplierCharacter);
            int times;
            int secs;
            try {
                if (timesSec.length > 1) {
                    times = Integer.parseInt(timesSec[0]);
                    secs = Integer.parseInt(timesSec[1]);
                } else {
                    times = 1;
                    secs = Integer.parseInt(timesSec[0]);
                }
            } catch (final NumberFormatException e) {
                throw new InvalidParameterException(
                        "Invalid number '"
                                + multi
                                + "' in '"
                                + raw
                                + "'. Expected format: [<multiplier>x]<seconds>;[<multiplier>x]<seconds>;...");
            }
            for (int ii = 0; ii != times; ++ii) {
                result.add(secs);
            }
        }
        final int[] ret = new int[result.size()];
        int ii = 0;
        for (final Integer sec : result) {
            ret[ii++] = sec;
        }
        return ret;
    }

    public static Set<InetAddress> parseRemoteAddresses(String raw) {
        if (raw == null || raw.length() == 0) {
            return null;
        }
        final String[] data = raw.split(",");
        final Set<InetAddress> result = new HashSet<>();
        for (final String multi : data) {
            try {
                result.add(InetAddress.getByName(multi));
            } catch (final UnknownHostException e) {
                log.error("Ignored unknown host : " + multi, e);
            }
        }
        return result;
    }

    public void removeSection(final SessionID sessionID) throws ConfigError {
        Properties properties = getSessionProperties(sessionID);

        if (properties == null) {
            throw new ConfigError("Session not found");
        }
        sections.remove(sessionID);
    }

    public void removeSection(final String propertyKey, final String propertyValue) throws ConfigError {
        SessionID sessionIdToRemove = sections.entrySet().stream()
                .filter(entry -> propertyValue.equals(entry.getValue().get(propertyKey)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ConfigError("Session not found"));

        sections.remove(sessionIdToRemove);
    }
}
