package org.gnu.mach;
import java.nio.ByteBuffer;

/**
 * Mach system calls.
 */
public class Mach {
    public static final long MSG_TIMEOUT_NONE = 0;

    /* Options for Mach.msg() calls. Copied from <mach/message.h>.
     *
     * XXX Ideally, we should be using an EnumSet, however there's no
     * clean, fast and portable way (across JREs) to convert them back
     * to the bitmap mach_msg() expects.
     *
     * As a middle-ground, I suggest we reserve the usage an option
     * argument to unsafe versions of msg(). An alternative would be to
     * encapsulate such bitmaps in their own class, however that would
     * probably be overengineered and clumsy to use.
     *
     * -- jk@jk.fr.eu.org 2011-06-30
     */
    public static final int MSG_OPTION_NONE = 0x00000000;
    public static final int SEND_MSG        = 0x00000001;
    public static final int RCV_MSG         = 0x00000002;
    public static final int SEND_TIMEOUT    = 0x00000010;
    public static final int SEND_NOTIFY     = 0x00000020;
    public static final int SEND_INTERRUPT  = 0x00000040;
    public static final int SEND_CANCEL     = 0x00000080;
    public static final int RCV_TIMEOUT     = 0x00000100;
    public static final int RCV_NOTIFY      = 0x00000200;
    public static final int RCV_INTERRUPT   = 0x00000400;
    public static final int RCV_LARGE       = 0x00000800;

    /**
     * Create a reply port.
     *
     * This is a wrapper around the mach_reply_port() system call, which
     * creates a new MachPort object with the returned name.
     */
    public static native int replyPort() throws Unsafe;

    /**
     * Native call to mach_msg().
     *
     * @param msg       Message buffer to operate on.
     * @param option    A bitwise-or combination of the SEND_* and RCV_*
     *                  options.
     * @param rcvName   Port to receive from (possibly @c null).
     * @param timeout   Timeout in milliseconds, or Mach.MSG_TIMEOUT_NONE
     *                  if no timeout has been selected.
     * @param notify    Notify port. XXX expand description.
     *
     * XXX repomper la doc du manuel.
     */
    public static native int
        msg(ByteBuffer msg, int option, int rcvName, long timeout, int notify)
        throws Unsafe;

    /**
     * This system call returns the calling thread's task port.
     *
     * mach_task_self has an effect equivalent to receiving a send right for
     * the task port. mach_task_self returns the name of the send right. In
     * particular, successive calls will increase the calling task's
     * user-reference count for the send right.
     *
     * As a special exception, the kernel will overrun the user reference
     * count of the task name port, so that this function can not fail for
     * that reason. Because of this, the user should not deallocate the port
     * right if an overrun might have happened. Otherwise the reference
     * count could drop to zero and the send right be destroyed while the
     * user still expects to be able to use it. As the kernel does not make
     * use of the number of extant send rights anyway, this is safe to do
     * (the task port itself is not destroyed, even when there are no send
     * rights anymore).
     *
     * The function returns MACH_PORT_NULL if a resource shortage prevented
     * the reception of the send right, MACH_PORT_NULL if the task port is
     * currently null, MACH_PORT_DEAD if the task port is currently dead.
     */
    public static native int taskSelf() throws Unsafe;

    /**
     * Task operations on ports.
     *
     * These calls are actually RPCs rather than system calls and this
     * functionality will eventually be replaced by MIG-generated stubs.
     */
    public static class Port {
        public static final int RIGHT_SEND = 0;
        public static final int RIGHT_RECEIVE = 1;
        public static final int RIGHT_SEND_ONCE = 2;
        public static final int RIGHT_PORT_SET = 3;
        public static final int RIGHT_DEAD_NAME = 4;

        public static native int allocate(int task, int right) throws Unsafe;
        public static native int deallocate(int task, int name) throws Unsafe;
    }
}

