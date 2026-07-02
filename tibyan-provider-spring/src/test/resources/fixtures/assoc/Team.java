package com.example.assoc;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.List;

/** Test fixture (not compiled): A5, a collection field refuses with the deferred-shape message. */
@Entity
public class Team {

    @Id
    private Long id;

    @OneToMany
    private List<Person> members;
}
