package com.example.mixed;

import org.springframework.stereotype.Service;

/** Test fixture (not compiled): the finder that backs {@code Alpha}, so {@code getAlpha} discovers. */
@Service
public class AlphaService {

    public Alpha findById(Long id) {
        return new Alpha();
    }
}
