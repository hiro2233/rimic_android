package bo.htakey.rimic.util;

/**
 * Called when a
 * Created by andrew on 01/03/17.
 */

@SuppressWarnings("serial")
public class RimicDisconnectedException extends RuntimeException {
    public RimicDisconnectedException() {
        super("Caller attempted to use the protocol while disconnected.");
    }

    public RimicDisconnectedException(String reason) {
        super(reason);
    }
}
