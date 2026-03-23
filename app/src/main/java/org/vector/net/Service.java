/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */

package org.vector.net;

import java.lang.annotation.*;

/**
 * Service configuration annotation for network service binding.
 * Marks a class as a network service and specifies the binding address and port
 * for client-server communication. This annotation is processed at runtime to
 * configure network endpoints for MMS server instances and other network services.
 * 
 * <p>Classes annotated with {@code @Service} must implement appropriate service interfaces
 * and will be automatically configured with the specified network binding during
 * initialization.</p>
 *
 * <p>If the {@code net} attribute is set to true, the service will automatically
 * determine and bind to the local IP address of the machine. Otherwise, the specified
 * address will be used for binding.</p>
 *
 * <p>The {@code enabled} attribute determines whether the annotation injection
 * functionality is active. If set to true, the service will be configured with
 * the specified network settings. If false, the service will not apply the
 * network configuration defined by this annotation.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Service(address = "192.168.190.130", port = 10240, net = false, enabled = true)
 * public class MyService extends ClientInitialize {
 *     // Service implementation
 * }
 * 
 * // Alternatively, to auto-detect the local IP address and enable the service:
 * @Service(port = 10240, net = true, enabled = true)
 * public class AutoDetectService extends ClientInitialize {
 *     // Service implementation
 * }
 * 
 * // To disable the annotation injection:
 * @Service(address = "192.168.190.130", port = 10240, enabled = false)
 * public class DisabledService extends ClientInitialize {
 *     // Service implementation
 * }
 * }</pre>
 *
 * @author Vectorted
 * @since 2026
 * @see java.lang.annotation.Retention
 * @see java.lang.annotation.Target
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    /**
     * Specifies the IP address or hostname for network service binding.
     * Defines the network interface address where the service will listen for
     * incoming client connections. Can be an IPv4 address, IPv6 address, or
     * hostname that resolves to a valid network interface.
     * 
     * @return IP address or hostname string for service binding
     */
    String address() default "0.0.0.0"; // Default to all available interfaces if not specified

    /**
     * Determines whether the service should automatically detect and bind to the local IP address.
     * If set to true, the service will ignore the {@code address} field and use the local IP.
     * 
     * @return true if the service should auto-detect the local IP address, false otherwise
     */
    boolean net() default false;

    /**
     * Determines whether the annotation injection functionality is enabled.
     * If set to true, the service will be configured with the specified network settings.
     * If false, the service will not apply the network configuration defined by this annotation.
     * 
     * @return true if the annotation injection is enabled, false otherwise
     */
    boolean enabled() default true;

    /**
     * Controls whether to instantiate the service class and start MMS/Slave server threads.
     * 
     * <p>When {@code true}: Injects the class and launches MMS/Slave service threads.</p>
     * <p>When {@code false}: Skips injection and keeps the service inactive.</p>
     * 
     * <p>Note: Independent of {@link #enabled()}. A service can be enabled but not initialized.</p>
     * 
     * @return {@code true} to inject class and start MMS/Slave threads, 
     *         {@code false} to skip injection
     * @see #enabled()
     */
    boolean initialize() default true;

    /**
     * Specifies the TCP port number for network service binding.
     * Defines the port on which the service will accept incoming client connections.
     * Must be in the valid range of 1-65535, with typical MMS server ports
     * usually configured in the range of 1024-65535 to avoid privileged ports.
     * 
     * @return TCP port number for service binding
     */
    int port();
}