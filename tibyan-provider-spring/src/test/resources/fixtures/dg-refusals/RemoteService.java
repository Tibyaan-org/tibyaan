package example.refuse;

import org.springframework.stereotype.Service;

/**
 * Test fixture (not compiled), DG-9. A cross-class delegation: the body forwards to another class and
 * performs no local load. There is nothing to bind, so it REFUSES as a delegation-only facade. The
 * private-helper hop must NOT step across a class boundary.
 */
@Service
public class RemoteService {

    private final RemoteClient remoteClient;

    public RemoteService(RemoteClient remoteClient) {
        this.remoteClient = remoteClient;
    }

    public RemoteVTO getById(Long id) {
        return remoteClient.getById(id);
    }
}
