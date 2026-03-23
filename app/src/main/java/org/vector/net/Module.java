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
 * Annotation that marks a class as a startup module within the application framework.
 * Classes annotated with @Module are recognized by the system as components requiring initialization.
 * The config parameter specifies which configuration file should be loaded for this module.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Module {
    
    /**
     * Specifies the configuration resource name to be loaded for this module.
     * The configuration file should contain initialization parameters and settings
     * specific to the annotated module class.
     *
     * @return The path or name of the configuration file to load
     */
    String config();
}