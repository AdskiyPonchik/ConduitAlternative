package de.conduit.articles;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@Tag(name = "Tags")
public class TagController {
    private final ArticleService articles;

    public TagController(ArticleService articles) {
        this.articles = articles;
    }

    @GetMapping({"", "/"})
    public TagsResponse getTags() {
        return new TagsResponse(articles.listTags());
    }

    public record TagsResponse(List<String> tags) {

    }
}
