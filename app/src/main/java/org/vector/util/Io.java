/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.stream.Stream;

import org.vector.Application;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;

/**
 * Provides utility methods for console I/O operations.
 * This class contains helper functions for common console tasks.
 */
public class Io {

    /**
     * Current working directory path where the application executes.
     * Stores the "user.dir" system property value as a constant.
     * 
     * @see System#getProperty(String)
     */
    public final static String WORKER_DIR = System.getProperty("user.dir");

    /**
     * Static reference to the original System.out PrintStream.
     * Captured at JVM startup to preserve the initial standard output stream.
     */
    final static PrintStream print = System.out;

    /**
     * Static reference to the original System.err PrintStream.
     * Captured at JVM startup to preserve the initial standard error stream.
     */
    final static PrintStream error = System.err;

    /**
     * Detects if an object was instantiated via reflection.
     * Checks stack trace for reflection API calls (Class.newInstance, Constructor.newInstance).
     * 
     * @param classz Object to check (parameter unused but kept for API compatibility)
     * @return true if reflection detected, false otherwise or if null
     * 
     * @note Performance impact: Uses stack trace analysis.
     * @note Parameter 'classz' is currently unused.
     */
    public static boolean isReflection(Object classz) {
        if (classz == null) {
            return false;
        }
        
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement query = stackTrace[i];
            
            if (query.getClassName().equals("java.lang.Class") && query.getMethodName().equals("newInstance")) {
                return true;
            }
            
            if (query.getClassName().equals("java.lang.reflect.Constructor") && query.getMethodName().equals("newInstance")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Disables console output by redirecting System.out to a null output stream.
     * All subsequent calls to System.out.print/println will be silently discarded.
     * Use openInput() to restore original output.
     */
    public static void closeInput() {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                
            }
        }));
        System.setErr(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                
            }
        }));
    }

    /**
     * Restores System.out to its original output stream.
     * Re-enables console output after closeInput() was called.
     */
    public static void openInput() {
        System.setOut(print);
        System.setErr(error);
    }

    /**
     * Loads a resource file from the classpath as a UTF-8 string.
     * 
     * @param file Path to the resource file (relative to classpath)
     * @return Content of the file as a string, or null if file not found
     */
    public static String getResources(String file) {
        InputStream is = Application.class.getClassLoader().getResourceAsStream(file);
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {}
        return null;
    }
    
    /**
     * Clears the console screen by executing the system's clear command.
     * This method uses the "clear" system command, which is compatible
     * with Unix-like operating systems (Linux, macOS). It may not work
     * correctly on Windows systems, which typically use the "cls" command.
     * 
     * The method suppresses all exceptions and performs no action if the
     * clear command fails or is interrupted. It inherits the current
     * process's standard I/O streams to maintain console connectivity.
     * 
     * Usage example:
     * <pre>
     * Io.clear(); // Clears the console display
     * </pre>
     * 
     * Note: This method creates a new system process, which may have
     * performance implications if called frequently.
     */
    public static void clear() {
        try {
            new ProcessBuilder("clear").inheritIO().start().waitFor();
        } catch (InterruptedException | IOException e) {}
    }
    
    /**
     * Recursively searches for a native shared library (.so) file starting from
     * the current working directory and loads it if found. If the library is not
     * found, the method logs an error and terminates the JVM process.
     *
     * @param libname the base name of the library (without the "lib" prefix and ".so" extension)
     */
    public static boolean loadLibrary(String libname, String... obj) {
        if(obj != null && obj.length > 0) {
            if(obj[0].equals("/")) {
                Loggor.log(Image.ERROR + Color.SPACE + Color.RED + "Shared library " + Color.YELLOW + "(" + libname + ")" + Color.RED + " not found, Jvm process has exited." + Color.RESET);
                System.exit(0);
                return false;
            }
        }

        String libroot = "lib" + libname + ".so";
        Path find = null;
        if(obj != null && obj.length > 0) {
            find = new File(obj[0]).toPath();
        } else find = new File(System.getProperty("user.dir")).toPath();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + libroot);

        File root = null;

        try (Stream<Path> stream = Files.walk(find)) {
            root = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {}
        if(root == null) {
            loadLibrary(libname, find.toFile().getParent());
            return false;
        }
        System.load(root.getAbsolutePath());
        return true;
    }
}
