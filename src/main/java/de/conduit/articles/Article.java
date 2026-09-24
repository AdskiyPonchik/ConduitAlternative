package de.conduit.articles;

import jakarta.persistence.*;

import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import de.conduit.articles.exception.InvalidArticleContentException;


@Entity
@Table(name = "articles")
public class Article {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int DESCRIPTION_MAX_LENGTH = 500;
    public static final int BODY_MAX_LENGTH = 100_000;
    public static final int TAG_MAX_LENGTH = 50;
    public static final int MAX_TAGS = 10;
    public static final int SLUG_MAX_LENGTH = 120;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, updatable = false, length = SLUG_MAX_LENGTH)
    private String slug;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @ElementCollection
    @CollectionTable(
            name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id")
    )
    @Column(name = "tag", nullable = false, length = TAG_MAX_LENGTH)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Article() {
    }

    public static Article create(
            UUID id,
            UUID authorId,
            String title,
            String description,
            String body,
            List<String> tagList,
            Instant now
    ) {
        Article article = new Article();
        article.id = Objects.requireNonNull(id, "id is required");
        article.authorId = Objects.requireNonNull(authorId, "authorId is required");
        article.title = requiredText(title, "title", TITLE_MAX_LENGTH, true);
        article.description = requiredText(
                description, "description", DESCRIPTION_MAX_LENGTH, true
        );
        article.body = requiredText(body, "body", BODY_MAX_LENGTH, false);
        article.slug = makeSlug(article.title, article.id);
        article.tags = normalizeTags(tagList);
        article.createdAt = Objects.requireNonNull(now, "now is required");
        article.updatedAt = now;
        return article;
    }

    private static Set<String> normalizeTags(List<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        if (values.size() > MAX_TAGS) {
            throw new InvalidArticleContentException("Too many tags");
        }

        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = value == null ? null : value.strip().toLowerCase(Locale.ROOT);
            result.add(requiredText(normalized, "tag", TAG_MAX_LENGTH, false));
        }
        return result;
    }

    private static String requiredText(
            String value, String field, int maximum, boolean strip
    ) {
        if (value == null || value.isBlank()) {
            throw new InvalidArticleContentException(field + " must not be blank");
        }
        String result = strip ? value.strip() : value;

        if (result.length() > maximum) {
            throw new InvalidArticleContentException(field + " is too long");
        }

        return result;
    }

    private static String makeSlug(String title, UUID id) {
        String prefix = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        prefix = prefix.substring(0, Math.min(prefix.length(), 80))
                .replaceAll("-+$", "");

        if (prefix.isEmpty()) {
            prefix = "article";
        }
        return prefix + "-" + id;
    }

    public boolean isAuthoredBy(UUID userID) {
        return authorId.equals(userID);
    }

    public void updateContent(String title, String description, String body, Instant now) {
        String nextTitle = title == null ? this.title : requiredText(title, "title", TITLE_MAX_LENGTH, true);
        String nextDescription = description == null ? this.description
                : requiredText(description, "description", DESCRIPTION_MAX_LENGTH, true);
        String nextBody = body == null ? this.body
                : requiredText(body, "body", BODY_MAX_LENGTH, false);
        Instant validNow = Objects.requireNonNull(now, "now is required");
        if (this.title.equals(nextTitle)
                && this.description.equals(nextDescription)
                && this.body.equals(nextBody)) {
            return;
        }

        this.title = nextTitle;
        this.description = nextDescription;
        this.body = nextBody;
        this.updatedAt = validNow;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getTagList() {
        return tags.stream().sorted().toList();
    }
}
