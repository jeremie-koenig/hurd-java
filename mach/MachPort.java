package org.gnu.mach;

/**
 * Mach port name.
 */
public class MachPort {
    static final int NULL = 0;
    static final int DEAD = -1;

    /**
     * Encapsulated port name.
     */
    private int name;

    /**
     * Instanciate a new MachPort object for the given name.
     *
     * This consumes one reference to @p name. The reference will be released
     * when deallocate() is called, or when the new object is collected.
     */
    private MachPort(int name) {
        this.name = name;
    }

    /**
     * Return the port name encapsulated by this object.
     *
     * FIXME: to be made (package-?) private.
     */
    public int name() {
        return name;
    }

    /**
     * Allocate a new port.
     *
     * TODO: this could eventually be replaced by the Java-based RPC call.
     */
    public static native MachPort allocate();

    /**
     * Deallocate this port.
     *
     * The current port name is deallocated and replaced with @c MACH_PORT_DEAD.
     */
    public synchronized void deallocate() {
        nativeDeallocate();
        this.name = DEAD;
    }

    /* Check that the port was deallocated when collected. */
    protected void finalize() {
        if(name == DEAD) return;
        System.err.println(String.format("MachPort: port %d was never deallocated", name));
        deallocate();
    }

    /**
     * Call mach_port_dellocate().
     *
     * TODO: this could eventually be replaced by the Java-based RPC call.
     */
    private native void nativeDeallocate();

    /* JNI code initialization */
    private static native void initIDs();
    static {
        initIDs();
    }
};

