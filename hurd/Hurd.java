package org.gnu.hurd;
import org.gnu.mach.MachPort;
import org.gnu.mach.Unsafe;

/**
 * Ambient authority of a Hurd process.
 */
public class Hurd {
    /**
     * Return the io server port for file descriptor FD.
     * This adds a Mach user reference to the returned port.
     */
    private native int unsafeGetdport(int fd) throws Unsafe;

    /**
     * Return a MachPort object for file descriptor FD.
     */
    public MachPort getdport(int fd) {
        try {
            return new MachPort(unsafeGetdport(fd));
        } catch(Unsafe e) {
            return null;
        }
    }
};

