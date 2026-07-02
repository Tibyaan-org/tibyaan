package com.example.banking;

import org.springframework.web.bind.annotation.RestController;

/**
 * Test fixture (not compiled). This controller has a method of exactly the matching shape
 * (returns Account, takes one Long), but it is a {@code @RestController}, not a {@code @Service}.
 * Rule 1 of the matching contract requires a {@code @Service}, so this must be ignored. If it were
 * not, the read would have two candidates and discovery would fail as ambiguous.
 */
@RestController
public class AccountController {

    public Account getAccount(Long id) {
        return null;
    }
}
