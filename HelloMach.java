import org.gnu.mach.MachPort;
import org.gnu.hurd.Hurd;

public class HelloMach {
    private static native void hello(MachPort port);

    public static void main(String argv[]) {
        Hurd hurd = new Hurd();
        System.loadLibrary("hurd-java");
        hello(hurd.getdport(1));
    }
}
