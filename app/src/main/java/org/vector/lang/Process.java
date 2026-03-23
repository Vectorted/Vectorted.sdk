/**
 * © Vectorted 2026. All rights reserved.
 * This software or content is protected by copyright law and may not be
 * reproduced, distributed, or modified without permission.
 * 
 * GitHub: https://github.com/Vectorted
 */
package org.vector.lang;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class Process {

    /**
     * Gets the PID of the first matching process by name.
     * 
     * @param processName Process name to search (case-insensitive partial match)
     * @return PID of the first found process, or -1 if no matching process is found
     */
    public static long getProcess(String processName) {
        try {
            return ProcessHandle.allProcesses()
                .filter(ph -> ph.info().command().isPresent())
                .filter(ph -> ph.info().command().get().toLowerCase()
                    .contains(processName.toLowerCase()))
                .map(ProcessHandle::pid)
                .collect(Collectors.toList()).getFirst();
        } catch (NoSuchElementException | IllegalStateException e) {
            return -1;
        }
    }

    /**
     * Gets PIDs of all running processes in the system.
     * 
     * @return List of all process PIDs
     */
    public static List<Long> getAllProcess() {
        return ProcessHandle.allProcesses()
            .map(ProcessHandle::pid)
            .collect(Collectors.toList());
    }
}
