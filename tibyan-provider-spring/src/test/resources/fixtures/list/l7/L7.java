package com.example.list.l7;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * L7: view-returning list. The element type is a projection DTO, not an in-tree @Entity, so there is
 * no entity collection to model. It is deferred as a view-returning list read, named, never guessed.
 */
@Entity
class Item {
    @Id
    Long id;
    String name;
}

class ItemDto {
}

interface ItemRepository extends JpaRepository<Item, Long> {
    List<ItemDto> findAllProjected();
}

@Service
class ItemService {

    private final ItemRepository repo;

    ItemService(ItemRepository repo) {
        this.repo = repo;
    }

    public List<ItemDto> getListable() {
        return repo.findAllProjected();
    }
}
