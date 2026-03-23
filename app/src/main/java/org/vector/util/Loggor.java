/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.util;

/**
 * Utility class for colored logging and symbol display.
 * Provides color management and pre-defined image symbols for consistent console output formatting.
 */
public class Loggor {

    /**
     * Color management class for ANSI terminal colors.
     * Encapsulates color types and provides predefined color constants and creation methods.
     */
    public static class Color {

        String TYPE;

        /**
         * Constructs a Color with specified ANSI color type.
         * @param type ANSI escape sequence representing the color
         */
        Color(String type) {
            this.TYPE = type;
        }

        /**
         * Returns the ANSI color escape sequence.
         * @return ANSI color escape sequence as String
         */
        public String color() {
            return this.TYPE;
        }

        /**
         * Creates an empty color with no formatting.
         * Used when no color formatting is desired.
         * @return Color instance with empty type
         */
        public static Color empty() {
            return new Color("");
        }

        /**
         * Creates a Color instance from specified type.
         * Returns empty color if provided type is null.
         * @param type ANSI color escape sequence or null for empty color
         * @return Color instance for the specified type
         */
        public static Color create(String type) {
            if(type == null) {
                return empty();
            }
            return new Color(type);
        }

        /**
         * ANSI reset code to clear all formatting.
         */
        public static String RESET = "\u001B[0m";

        /**
         * ANSI green color code for success messages.
         */
        public static String GREEN = "\u001B[32m";

        /**
         * ANSI yellow color code for warning messages.
         */
        public static String YELLOW = "\u001B[33m";

        /**
         * ANSI blue color code for ok messages.
         */
        public static String BLUE = "\u001B[34m";

        /**
         * ANSI blue color code for error messages.
         */
        public static String RED = "\u001B[31m";

        /**
         * Single space character for formatting.
         */
        public static String SPACE = "\u0020";
    }

    /**
     * Image symbol class for pre-formatted colored symbols.
     * Combines color and symbol type into formatted output strings.
     */
    public static class Image {

        String image;

        /**
         * Constructs an Image with specified color and symbol type.
         * Formats the symbol by applying color and reset sequence.
         * @param color Color instance for formatting
         * @param type Symbol character or string to be formatted
         */
        Image(Color color, String type) {
            this.image = color.color() + type + Color.RESET;
        }

        /**
         * Returns the formatted image string with color applied.
         * @return formatted symbol string with ANSI color codes
         */
        public String image() {
            return this.image;
        }

        /**
         * Pre-defined arrow symbol in yellow color (➜).
         * Commonly used as command prompt indicator.
         */
        public static String ARROW = new Image(Color.create(Color.YELLOW), "➜").image();

        /**
         * Pre-defined check mark symbol in green color (✔).
         * Commonly used to indicate successful operations.
         */
        public static String CHECK = new Image(Color.create(Color.GREEN), "✔").image();

        /**
         * Pre-defined double angle symbol with no color formatting (»).
         * Commonly used as section divider or emphasis marker.
         */
        public static String DOUBLE_ANGLE = new Image(Color.empty(), "»").image();

        /**
         * Pre-defined warning symbol image with yellow color formatting (✖).
         * This static constant represents a visual warning/error indicator typically used in UI
         * components to highlight issues, validation errors, or attention-required states.
         * 
         * The symbol uses a yellow color theme to distinguish it from critical errors (red)
         * and informational messages (blue/green).
         */
        public static String ERROR = new Image(Color.create(Color.YELLOW), "✖").image();
    }
    
    /**
     * Logs a message to standard output.
     * Prints the object's string representation followed by newline.
     * @param message object to be logged to console
     */
    public static void log(Object message) {
        System.out.println(message);
    }

    /**
     * Logs a message to standard output. | Not NUll
     * Prints the object's string representation followed by newline.
     * @param message object to be logged to console
     */
    public static void print(Object handNotNullMessage) {
        if(handNotNullMessage == null) return;

        System.out.println(handNotNullMessage);
    }
}