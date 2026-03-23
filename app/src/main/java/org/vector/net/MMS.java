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
 * Marker annotation to indicate that a class implements IEC 61850 MMS protocol functionality.
 * This annotation serves solely as a documentation marker and has no functional behavior
 * or runtime effects. It is used to explicitly mark classes that are part of the MMS
 * protocol implementation for identification and documentation purposes only.
 * 
 * <p>This annotation does not influence program logic, configuration, or runtime behavior.
 * It purely serves as semantic metadata to indicate MMS protocol compliance and facilitate
 * code understanding and maintenance.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @MMS
 * public class App extends ClientInitialize {
 *     // IEC 61850 MMS protocol implementation
 * }
 * }</pre>
 *
 * @author Vectorted
 * @since 2026
 * @see java.lang.annotation.Documented
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MMS {
    
}