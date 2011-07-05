import org.gnu.mach.Mach;
import org.gnu.mach.MachPort;
import org.gnu.mach.MachMsg;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static void hello(MachPort stdout) throws Exception {
        MachMsg msg = new MachMsg(1000);
        MachPort reply = Mach.replyPort();

        /* mach_msg_header_t */
        msg.setRemotePort(stdout, MachMsg.Type.COPY_SEND);
        msg.setLocalPort(reply, MachMsg.Type.MAKE_SEND_ONCE);
        msg.setId(21000);

        /* Data */
        msg.putChar("Hello in Java!\n".getBytes());

        /* Offset */
        msg.putInteger64(-1);

        int err = Mach.msg(msg.buf, Mach.SEND_MSG | Mach.RCV_MSG, reply, Mach.MSG_TIMEOUT_NONE, null);
        System.out.println("err = " + err);
        msg.buf.position(msg.buf.getInt(4)).flip().position(24);

        int retcode = msg.getInteger32();
        System.out.println("retcode = " + retcode);

        int amount = msg.getInteger32();
        System.out.println("amount = " + amount);

        reply.deallocate();
        msg.clear();
    }

    public static void testHello() throws Exception {
        Hurd hurd = new Hurd();
        MachPort stdoutp = hurd.getdport(1);
        hello(stdoutp);
        stdoutp.deallocate();
    }

    public static void main(String argv[]) throws Exception {
        System.loadLibrary("hurd-java");

        testHello();

        for(int i = 0; i < 100; i++) {
            byte[] foo = new byte[10000000];
            System.gc();
        }
    }
}
