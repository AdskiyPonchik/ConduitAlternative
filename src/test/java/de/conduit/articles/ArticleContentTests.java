package de.conduit.articles;

import de.conduit.articles.exception.InvalidArticleContentException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class ArticleContentTests {
    private final Instant before = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void invalidLaterFieldDoesNotPartiallyChangeTheEntity() {
        Article article = article();
        assertThatThrownBy(() -> article.updateContent("New title", null, "   ", before.plusSeconds(1)))
                .isInstanceOf(InvalidArticleContentException.class);
        assertThat(article.getTitle()).isEqualTo("Title");
        assertThat(article.getBody()).isEqualTo("Body");
        assertThat(article.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    void noChangeKeepsTimestampAndRealChangesKeepSlugAndMarkdownWhitespace() {
        Article article = article();
        String slug = article.getSlug();
        article.updateContent(null, null, null, before.plusSeconds(1));
        assertThat(article.getUpdatedAt()).isEqualTo(before);
        article.updateContent(" Title ", null, null, before.plusSeconds(2));
        assertThat(article.getUpdatedAt()).isEqualTo(before);

        article.updateContent("New title", null, "  Markdown\n", before.plusSeconds(3));
        assertThat(article.getSlug()).isEqualTo(slug);
        assertThat(article.getBody()).isEqualTo("  Markdown\n");
        assertThat(article.getDescription()).isEqualTo("Description");
        assertThat(article.getUpdatedAt()).isEqualTo(before.plusSeconds(3));
    }

    private Article article() {
        return Article.create(UUID.randomUUID(), UUID.randomUUID(), "Title", "Description", "Body", List.of("java"), before);
    }
}
