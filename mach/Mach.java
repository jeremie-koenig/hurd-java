package org.gnu.mach;
import java.nio.ByteBuffer;

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
    public static native int msg(ByteBuffer msg, int option,
            MachPort rcvName, long timeout, MachPort notify);
}

