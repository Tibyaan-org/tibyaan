package com.example.dto.f4;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;

/**
 * F4: name-similar but structurally unrelated. The method returns TenantVto and is named getTenant,
 * but it performs no persistence load at all. Names carry no weight, so it must not match getTenant;
 * the entity refuses with NO_BACKING_METHOD. This is the mandated proof that names do not match.
 */
@Entity
class Tenant {
    @Id
    Long id;
    String name;
}

class TenantVto {
}

class TenantMapper {
    TenantVto fromId(Long id) {
        return null;
    }
}

@Service
class NameTrapService {

    private final TenantMapper mapper;

    NameTrapService(TenantMapper mapper) {
        this.mapper = mapper;
    }

    public TenantVto getTenant(Long id) {
        return mapper.fromId(id);
    }
}
