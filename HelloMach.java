import org.gnu.mach.Mach;
import org.gnu.mach.MachPort;
import org.gnu.mach.MachMsg;
import org.gnu.mach.MachMsgType;
import org.gnu.mach.TypeCheckException;
import org.gnu.mach.Unsafe;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static void hello(MachPort stdout)
        throws TypeCheckException
    {
        MachMsg msg = new MachMsg(1000);
        MachPort reply = MachPort.allocateReplyPort();

        /* mach_msg_header_t */
        msg.setRemotePort(stdout, MachMsgType.COPY_SEND);
        msg.setLocalPort(reply, MachMsgType.MAKE_SEND_ONCE);
        msg.setId(21000);

        /* Data */
        msg.putBytes("Hello in Java!\n".getBytes());

        /* Offset */
        msg.putLong(-1);

        try {
            int err = Mach.msg(msg.buf(),
                               Mach.SEND_MSG | Mach.RCV_MSG,
                               reply.name(),
                               Mach.MSG_TIMEOUT_NONE,
                               Mach.Port.NULL);
            reply.releaseName();
            System.out.println("err = " + err);
            msg.flip();
        } catch(Unsafe e) {}

        int retcode = msg.getInt();
        System.out.println("retcode = " + retcode);

        int amount = msg.getInt();
        System.out.println("amount = " + amount);

        reply.deallocate();
        msg.clear();
    }

    public static void testHello() throws TypeCheckException {
        Hurd hurd = new Hurd();
        MachPort stdoutp = hurd.getdport(1);
        hello(stdoutp);
        stdoutp.deallocate();
    }

    public static void main(String argv[]) throws TypeCheckException {
        System.loadLibrary("hurd-java");

        testHello();

        for(int i = 0; i < 100; i++) {
            byte[] foo = new byte[10000000];
            System.gc();
        }
    }
}
