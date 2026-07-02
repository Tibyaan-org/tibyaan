package com.example.dto.f10;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;

/**
 * F10: a delegation-only facade (the proxy shape). The method delegates to another service and
 * performs no persistence load in its own body, so there is no load to verify. Condition 4 refuses.
 */
@Entity
class Prod {
    @Id
    Long id;
    String name;
}

class ProdVto {
}

interface ProdApi {
    ProdVto findById(Long id);
}

@Service
class ProdProxyService {

    private final ProdApi delegate;

    ProdProxyService(ProdApi delegate) {
        this.delegate = delegate;
    }

    public ProdVto findById(Long id) {
        return delegate.findById(id);
    }
}
