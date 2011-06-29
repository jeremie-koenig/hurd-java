package org.gnu.hurd;
import org.gnu.mach.MachPort;

/**
 * Ambient authority of a Hurd process.
 */
public class Hurd {
    /**
     * Retreive the MachPort for a given file descriptor.
     */
    public native MachPort getdport(int fd);
};

