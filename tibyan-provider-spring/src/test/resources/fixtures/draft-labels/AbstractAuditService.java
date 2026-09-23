package com.example.draft;

import org.springframework.stereotype.Service;

/** Skipped by DL-0: an abstract service class is not a callable bean of its own. */
@Service
public abstract class AbstractAuditService {

    public void audit(Long id) {
    }

    public abstract void record(String event);
}
