/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.util.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration file loader for INI-style property files.
 * 
 * This class provides a simplified interface for reading key-value pairs
 * from configuration files, primarily used for loading IEC 61850 server settings.
 * It leverages Java's standard Properties class to parse basic INI files without sections.
 * 
 * Note: This implementation only supports simple key-value format without section headers.
 * For full INI file support with sections, use a dedicated INI parsing library.
 * 
 * This class uses a factory pattern through the static `init()` method for instance creation.
 */
public class Config {

    /** Internal Properties object storing the loaded configuration key-value pairs */
    Properties properties;

    /**
     * Static factory method to create and initialize a Config instance.
     * This is the preferred way to instantiate Config objects, providing a clean API.
     * 
     * @param ini The path to the INI configuration file to load
     * @return A fully initialized Config instance with the loaded properties
     * 
     * @example Config config = Config.init("settings.ini");
     */
    public static Config init(String ini) {
        return new Config(ini);
    }
    
    /**
     * Private constructor that loads the specified INI configuration file.
     * The configuration file should contain simple key-value pairs (one per line).
     * 
     * @param ini The path to the INI configuration file to load
     * 
     * @note Silently ignores IO exceptions; consider adding logging for production use.
     * @note Only supports the simple "key=value" format, not full INI sections.
     * @note File encoding is platform-dependent (typically ISO-8859-1 for Properties).
     */
    Config(String ini) {
        this.properties = new Properties();
        try (InputStream input = new FileInputStream(ini)) {
            this.properties.load(input);
        } catch (IOException e) {}
    }

    /**
     * Retrieves the string value associated with the specified configuration key.
     * 
     * @param key The configuration key to look up (case-sensitive)
     * @return The string value associated with the key, or null if the key doesn't exist
     * 
     * @see #properties
     * @note Returns null for non-existent keys; consider providing default values.
     * @example getValue("PORT") returns "10240" from the INI file
     */
    public String getValue(String key) {
        return this.properties.getProperty(key);
    }
}