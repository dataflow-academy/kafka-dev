package academy.dataflow.wind.hello;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Lab scaffolding — not part of the exercise. It keeps the lab observable and
 * safe to break; you would not write this in a production client.
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
