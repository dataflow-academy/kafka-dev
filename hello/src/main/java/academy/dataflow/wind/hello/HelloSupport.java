package academy.dataflow.wind.hello;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Lab scaffolding, not part of the exercise. It keeps the lab observable and
 * safe to break; a production client would not need any of it.
 */
final class HelloSupport {

    /** The host name, used as the key so you can tell whose message is whose. */
    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    private HelloSupport() {
    }
}
