package org.gnu.mach;

/**
 * Type check exception.
 *
 * This exception is raised by {@code MachMsg.get*} operations when the
 * type descriptor read from the message does not match the expected
 * value.
 */
public class TypeCheckException extends Exception {
    static final long serialVersionUID = -8432763016000561949L;

    public TypeCheckException() {
        super();
    }
    public TypeCheckException(String msg) {
        super(msg);
    }
}
