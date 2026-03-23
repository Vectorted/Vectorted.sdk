/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector;

import org.vector.client.VectortedInitialize;
import org.vector.net.JDK;
import org.vector.net.Service;
import org.vector.net.Module;
import org.vector.util.Loggor;
import org.vector.util.Loggor.Color;
import org.vector.util.Loggor.Image;
import org.vector.util.config.Config;
import org.vector.worker.Handler;

import java.io.Console;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientInitializer {
    
    /**
     * Singleton instance of ClientInitialize class.
     * Uses double-checked locking pattern with volatile for thread safety.
     */
    private static volatile VectortedInitialize instance = null;

    /**
     * Atomic flag tracking initialization state.
     * Ensures initialization happens only once across all threads.
     */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Application running state flag.
     * Controls main loop execution; set to false to stop the application.
     */
    private static volatile boolean running = true;

    /**
     * Checks if the current JDK version is compatible with the specified version.
     * 
     * <p>This method uses reflection to obtain the current JDK major version,
     * supporting Java 1.8 through 21+.
     * 
     * <p>For Java 9 and above, it uses {@code Runtime.version().feature()} to
     * get the major version. For Java 8 and below, it parses the {@code java.version}
     * system property.
     * 
     * @param version the JDK major version to check (e.g., 8, 11, 17)
     * @return {@code true} if the specified version equals the current version,
     *         or is between 6 and the current version (inclusive); {@code false} otherwise
     * @throws NoSuchMethodException if critical reflection methods are unavailable
     */
    static boolean loadJdk(int version) {
        try {
            Class<?> runtimeClass = Class.forName("java.lang.Runtime");
            Class<?> versionClass = Class.forName("java.lang.Runtime$Version");
            Method versionMethod = runtimeClass.getMethod("version");
            Method feature = versionClass.getMethod("feature");

            versionMethod.setAccessible(true);
            feature.setAccessible(true);

            if(versionMethod != null) {
                Object versionClassObject = versionMethod.invoke(null);
                int vn = (Integer)feature.invoke(versionClassObject);

                return version == vn || (version <= vn && version >= 6);
            }
        } catch (Exception e) {e.printStackTrace();}
        return version <= 8 && version >= 6;
    }

    /**
     * Initializes and starts a client based on the specified protocol type.
     * For "MMS": starts an IEC 61850 client.
     * For "SLAVE": starts an IEC 104 client.
     *
     * @param type        The protocol type ("MMS" or "SLAVE").
     * @param constructor The constructor for the client instance.
     * @param config      Configuration object containing port settings.
     * @param address     The server address to connect to.
     * @param _port       The port to use. If 0, falls back to the port defined in config.
     */
    static void startType(String type, Constructor<?> constructor, Config config, String address, int _port) throws NumberFormatException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        int mms_port = (_port == 0) ? Integer.parseInt(config.getValue("IEC61850-PORT")) : _port;
        int slave_port = (_port == 0) ? Integer.parseInt(config.getValue("IEC104-PORT")) : _port;

        switch (type) {
            case "MMS":
            instance = (VectortedInitialize) constructor.newInstance(config, address, mms_port);
            instance.initialize();

            break;

            case "SLAVE":
            instance = (VectortedInitialize) constructor.newInstance(config, address, slave_port);
            instance.initializeSlave();

            break;
        }
    }
    
    /**
     * Reflectively executes client class ensuring single instance.
     * @param clientClass client class extending ClientInitialize with @Service annotation
     */
    @SuppressWarnings("null")
    public static void runModule(Class<?> module) {
        if (module == null) {
            return;
        }
        
        if (!VectortedInitialize.class.isAssignableFrom(module)) {
            return;
        }
        
        if (!module.isAnnotationPresent(Service.class) && !module.isAnnotationPresent(JDK.class) && !module.isAnnotationPresent(Module.class)) {
            return;
        }
        
        if (initialized.get()) {
            return;
        }
        
        synchronized (ClientInitializer.class) {
            if (initialized.get()) {
                return;
            }
            
            try {
                Service serviceAnnotation = module.getAnnotation(Service.class);
                Module moduleConfig = module.getAnnotation(Module.class);
                JDK jdk = module.getAnnotation(JDK.class);

                String address = serviceAnnotation.address();
                String configPath = moduleConfig.config();
                int port = serviceAnnotation.port();
                boolean net = serviceAnnotation.net();
                boolean enabled = serviceAnnotation.enabled();
                boolean initialize = serviceAnnotation.initialize();

                if(!loadJdk(jdk.version())) {
                    Loggor.log(Color.RED + "Class " + Color.GREEN + "(" + module.getName() + Color.GREEN + ")" + Color.RED + " does not execute JDK version correctly and will not be executed." + Color.RESET);
                    return;
                }

                if(!initialize) return;
                Config config = Config.init(configPath);

                if (net) {
                    String mode = config.getValue("IP-MODE");
                    
                    switch (mode) {
                        case "IPv4":
                            address = getFirstNonLoopbackIPv4Address();
                            break;
                    
                        case "IP":
                            address = config.getValue("IP");
                            break;
                    }
                    if (address == null) {
                        return;
                    }
                }

                Constructor<?> constructor = module.getDeclaredConstructor(Config.class, String.class, int.class);
                constructor.setAccessible(true);
                
                startType(config.getValue("TYPE"), constructor, config, address, enabled ? 0 : port);
                initialized.set(true);
                
                registerShutdownHook();
                startConsoleInput();
                
            } catch (Exception e) {e.printStackTrace();}
        }
    }

    /**
     * Retrieves the first non-loopback IPv4 address of the local machine.
     * This method mimics the behavior of the Linux command `ip addr show`.
     * 
     * @return the first non-loopback IPv4 address as a string, or null if not found
     */
    private static String getFirstNonLoopbackIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {}
        return null;
    }
    
    /**
     * Starts console input loop for command processing.
     */
    private static void startConsoleInput() {
        Thread inputThread = new Thread(() -> {
            while (running && initialized.get()) {
                try {
                    Console console = System.console();
                    if (console == null) {
                        return;
                    }
                    
                    String command = console.readLine(Image.DOUBLE_ANGLE + Color.SPACE);
                    if (instance != null && instance instanceof Handler) {
                        ((VectortedInitialize) instance).onCommand(command.split(" "));
                    }
                    
                } catch (Exception e) {}
            }
        });
        inputThread.setDaemon(true);
        inputThread.start();
    }
    
    /**
     * Registers JVM shutdown hook to invoke exit method.
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            if (instance != null && instance instanceof Handler) {
                try {
                    VectortedInitialize handler = (VectortedInitialize) instance;
                    handler.exit();
                } catch (Exception e) {}
            }
            cleanup();
        }));
    }
    
    /**
     * Cleans up resources.
     */
    private static void cleanup() {
        synchronized (ClientInitializer.class) {
            instance = null;
            initialized.set(false);
            running = false;
        }
    }
    
    /**
     * Gets current instance for testing and debugging.
     * @return current ClientInitialize instance
     */
    public static VectortedInitialize getInstance() {
        return instance;
    }
    
    /**
     * Checks if client is initialized.
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
}