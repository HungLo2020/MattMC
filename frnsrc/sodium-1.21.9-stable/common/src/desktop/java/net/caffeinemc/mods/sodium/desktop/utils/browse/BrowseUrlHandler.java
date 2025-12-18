package frnsrc.sodium;

import java.io.IOException;

public interface BrowseUrlHandler {
    void browseTo(String url) throws IOException;

    static BrowseUrlHandler createImplementation() {
        // OpenJDK doesn't use xdg-open and fails to provide an implementation on most systems.
        if (XDGImpl.isSupported()) {
            return new XDGImpl();
        } else if (CrossPlatformImpl.isSupported()) {
            return new CrossPlatformImpl();
        }

        return null;
    }
}
