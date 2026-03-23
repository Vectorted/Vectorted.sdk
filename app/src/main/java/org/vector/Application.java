/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector;

import static org.vector.ClientInitializer.runModule;

public class Application {
    /**
     * The standard main method for a Java application.
     * It starts the application by launching the Vectorted framework's main module.
     * Any module started must be a subclass of Vectorted and comply with its modular startup specification.
     *
     * @param args command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        // Launch the Vectorted framework's main module.
        runModule(Vectorted.class);
    }
}
