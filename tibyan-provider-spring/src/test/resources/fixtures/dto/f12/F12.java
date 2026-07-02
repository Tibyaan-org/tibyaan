package com.example.dto.f12;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;

/**
 * F12: a composite-key entity. Two @Id fields under @IdClass mean the entity forms no read intent
 * and is never read by part of its key. Condition 0 refuses with the composite-key cause.
 */
@Entity
@IdClass(MembershipId.class)
class Membership {
    @Id
    Long groupId;
    @Id
    Long userId;
    String role;
}

class MembershipId {
    Long groupId;
    Long userId;
}
