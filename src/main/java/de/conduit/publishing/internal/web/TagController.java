package de.conduit.publishing.internal.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags/")
public class TagController {

    @GetMapping
    public TagsResponse getTags() {
        return new TagsResponse(List.of());
    }

    public record TagsResponse(List<String> tags) {
    }
}
