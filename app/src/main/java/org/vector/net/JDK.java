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
 * Specifies the required JDK version for a class or interface.
 * The JNI layer validates this annotation at runtime and prevents execution
 * if the current JDK version does not meet the requirement.
 * 
 * @author Vectorted
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JDK {
    /**
     * Defines the minimum required JDK version.
     * The version number should follow the standard JDK versioning scheme
     * (e.g., 8 for JDK 8, 11 for JDK 21).
     * 
     * @return the required JDK version as an integer
     */
    int version();
}