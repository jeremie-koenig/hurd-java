public class HelloMach {
    private static native void hello();

    public static void main(String argv[]) {
        System.loadLibrary("HelloMach");
        hello();
    }
}
