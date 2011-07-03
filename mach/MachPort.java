package org.gnu.mach;

/**
 * Opaque Mach port name.
 *
 * This class encapsulates {@code mach_port_t} values in a safe manner.
 * Users of the {@link org.gnu.mach} package never manipulate port names
 * directly. Instead, they refer to ports using MachPort objects.
 *
 * MachPort objects generally behave the same way {@code mach_port_t}
 * values do. They are created when incoming RPC messages are parsed.
 * The {@link #equals()} method can be used to determine whether two send
 * right refer to the same object (XXX not implemented yet).
 *
 * <h3>Deallocation</h3>
 *
 * <h3>Internals</h3>
 *
 * Behind the scenes, classes from the {@link org.gnu.mach} package can
 * acquire the actual {@code mach_port_t} value encapsulated by a MachPort
 * object. To preserve safety, we must ensure that no matter what happens,
 * this value remains valid between the time it's acquired and the time it
 * is actually used.
 *
 * The most obvious solution to this problem would be to increment the port's
 * user reference count using the {@code mach_port_mod_refs()} call. However,
 * this would incur a significant cost since two such calls would be needed
 * for every port name included in a message.
 *
 * Instead, every MachPort object maintains a reference counter which is
 * incremented every time the port name is acquired with {@link #name()}.
 * A non-zero reference counter prevents the object from being deallocated
 * right away. Instead, if {@link #deallocate()} is called, the actual
 * deallocation of the port name at the Mach level will be postponed until
 * the reference counter falls back to zero.
 *
 * <h3>JNI interface</h3>
 */
public class MachPort {
    public static final MachPort NULL = new MachPort( 0);
    public static final MachPort DEAD = new MachPort(~0);

    /**
     * Encapsulated port name.
     */
    private int name;

    /**
     * Name reference count.
     */
    private int refCnt;

    /**
     * Whether a deallocation request is pending.
     */
    private boolean deallocPending;

    /**
     * Instanciate a new MachPort object for the given name.
     *
     * This consumes one reference to @p name. The reference will be released
     * when deallocate() is called, or when the new object is collected.
     */
    private MachPort(int name) {
        this.name = name;
        refCnt = 0;
        deallocPending = false;
    }

    /**
     * Get the port name associated with this object.
     *
     * This unsafe operation permits access to the port name encapsulated in
     * this MachPort object. To prevent the name from being rendered invalid
     * by deallocation after it has been obtained, calling this method will
     * increment a reference counter. A subsequent deallocation request will
     * be delayed until {@link #releaseName()} is called.
     *
     * FIXME: to be made (package-?) private.
     */
    public synchronized int name() {
        refCnt++;
        return name;
    }

    /**
     * Release a reference acquired through name().
     */
    public synchronized void releaseName() {
        assert refCnt > 0;
        refCnt--;

        if(refCnt == 0 && deallocPending) {
            deallocPending = false;
            deallocate();
        }
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
     * The port name is deallocated and replaced with {@code MACH_PORT_DEAD}.
     */
    public synchronized void deallocate() {
        if(refCnt > 0) {
            /* Postpone */
            deallocPending = true;
            return;
        }
        nativeDeallocate();
        name = DEAD.name;
        deallocPending = false;
    }

    /* Check that the port was deallocated and has no references left at
     * collection time. */
    protected void finalize() {
        if(refCnt > 0) {
            System.err.println(String.format(
                        "MachPort: port name %d was never released", name));
            refCnt = 0;
        }
        if(name != DEAD.name) {
            System.err.println(String.format(
                        "MachPort: port %d was never deallocated", name));
            deallocate();
        }
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

