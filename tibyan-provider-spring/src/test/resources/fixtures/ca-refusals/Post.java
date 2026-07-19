package ca.refusals;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PostAuthorize;

@Entity
class Post {
    @Id
    Long id;
}

@Service
class PostService {
    public Post findById(Long id) {
        return null;
    }
}

@RestController
class PostController {
    private final PostService service;

    PostController(PostService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    @PostAuthorize("returnObject.owner == authentication.name")
    public Post getPost(@PathVariable Long id) {
        return service.findById(id);
    }
}
