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
 * <h3>Unsafe access</h3>
 *
 * The encapsulated port name can be retreived by unsafe code using the
 * {@link #name} and {@link #clear} methods.
 *
 * To ensure the port name remains valid between the time it's acquired and
 * the time it is actually used, {@link #name} increments an external
 * reference counter. Calls to {@link #clear} and {@link #deallocate} will
 * block until the external reference is released using {@link #releaseName}.
 *
 * By contrast, {@link #clear} replaces the encapsulated port name with
 * {@code MACH_PORT_DEAD} and returns its previous value to the caller,
 * which becomes responsible for the corresponding Mach user reference
 * previously associated with the {@link MachPort} object.
 */
public class MachPort {
    public static MachPort NULL;
    public static MachPort DEAD;

    static {
        try {
            NULL = new MachPort( 0);
            DEAD = new MachPort(~0);
        } catch(Unsafe e) { }
    }

    /**
     * Port rights for {@link MachPort#allocate}.
     */
    public static enum Right {
        SEND,
        RECEIVE,
        SEND_ONCE,
        PORT_SET,
        DEAD_NAME;
    }

    /**
     * Encapsulated port name.
     */
    private int name;

    /**
     * Name reference count.
     */
    private int refCnt;

    /**
     * Instanciate a new MachPort object for the given name.
     *
     * This consumes one reference to @p name. The reference will be released
     * when deallocate() is called, or when the new object is collected.
     */
    @SuppressWarnings("unused")
    public MachPort(int name) throws Unsafe {
        this.name = name;
        refCnt = 0;
    }

    /**
     * Acquire the port name associated with this object.
     *
     * This unsafe operation permits access to the port name encapsulated in
     * this MachPort object. To prevent the name from being rendered invalid
     * by deallocation after it has been obtained, calling this method will
     * increment the external reference counter. Any deallocation request
     * will block until {@link #releaseName()} is called.
     */
    @SuppressWarnings("unused")
    public synchronized int name() throws Unsafe {
        refCnt++;
        return name;
    }

    /**
     * Release a reference acquired through name().
     */
    @SuppressWarnings("unused")
    public synchronized void releaseName() throws Unsafe {
        assert refCnt > 0;
        refCnt--;
        notifyAll();
    }

    /**
     * Replace the port name encapsulated by this object with MACH_PORT_DEAD.
     *
     * The previous port name is not deallocated, but is instead returned to
     * the caller. It is the caller's responsability to ensure that the
     * corresponding Mach-level port right reference does not leak.
     *
     * If external references to the port name exist, the call will block
     * until they have all been released.
     */
    @SuppressWarnings("unused")
    public synchronized int clear() throws Unsafe {
        if(name != DEAD.name)
            while(refCnt > 0)
                try {
                    wait();
                } catch(InterruptedException exc) {
                    /* ignore */
                }

        int oldName = name;
        name = DEAD.name;
        return oldName;
    }

    /**
     * Deallocate this port.
     *
     * The encapsulated port name is replaced with {@code MACH_PORT_DEAD}.
     * If external references to the port name exist, the call will block
     * until they have all been released.
     */
    public synchronized void deallocate() {
        try {
            int name = clear();
            Mach.Port.deallocate(Mach.taskSelf(), name);
        } catch(Unsafe e) {}
    }

    /**
     * Allocate a new reply port.
     */
    public static MachPort allocateReplyPort() {
        try {
            int name = Mach.replyPort();
            return new MachPort(name);
        } catch(Unsafe e) {
            return null;
        }
    }

    /**
     * Allocate a new port name.
     */
    public static MachPort allocate(Right right) {
        try {
            int name = Mach.Port.allocate(Mach.taskSelf(), right.ordinal());
            return new MachPort(name);
        } catch(Unsafe e) {
            return null;
        }
    }

    /**
     * Allocate a new receive port right.
     */
    public static MachPort allocate() {
        return allocate(Right.RECEIVE);
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
};

