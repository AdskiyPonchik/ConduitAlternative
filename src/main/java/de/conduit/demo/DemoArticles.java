package de.conduit.demo;

import java.util.List;

final class DemoArticles {
    record Example(String title, String description, String body, List<String> tags) {}

    static List<Example> examples() {
        return List.of(
            article("A transaction is an operation boundary", "Why service methods define transactions in Conduit.",
                "A service can update several rows in one operation. Spring opens the transaction around the service method.\n\nHibernate flushes pending changes before PostgreSQL commits. A flush is not a commit: a later failure can still roll everything back.", "java", "spring"),
            article("Paginate articles before fetching tags", "How a page keeps its intended number of articles.",
                "Joining an article to its tags multiplies rows. Applying a limit to that join can cut an article's tag collection or return fewer distinct articles.\n\nConduit first selects an ordered page of IDs, loads those articles with their tags, then restores the ID order in memory.", "postgresql", "pagination"),
            article("Keep stale responses off the screen", "A small rule for asynchronous interfaces.",
                "Switch a filter twice on a slow connection. The first request can finish after the second.\n\nA request generation counter lets only the latest result change the screen. The same check protects loading and error state.", "vue", "typescript"),
            article("Authentication and ownership are separate", "A signed token does not authorize every edit.",
                "Spring Security validates a JWT and exposes its subject as the current actor.\n\nThe service compares that actor with the article author before changing content. Another valid account still receives HTTP 403.", "java", "security"),
            article("Composite keys make favorites unique", "Repeated requests should not create duplicate relationships.",
                "The primary key on article_favorites contains both article_id and user_id.\n\nAn INSERT with ON CONFLICT DO NOTHING makes repeated favorites idempotent. The database enforces uniqueness even when requests race.", "postgresql", "concurrency"),
            article("Make request failures visible", "Loading, empty results and errors are different states.",
                "An empty feed is a valid result. A failed request is not an empty feed.\n\nThe interface shows an error and a retry action while retaining a clear loading state. Try temporarily stopping the API in your local environment.", "vue", "ux"),
            article("Use a clock you can replace", "Deterministic timestamps make behavior easier to test.",
                "Services receive java.time.Clock through constructor injection. They do not each choose a system clock.\n\nTests can replace that clock with a fixed instant to check timestamps and token expiry without sleeping.", "java", "testing"),
            article("Foreign keys describe real dependencies", "Comments belong to articles and authors.",
                "A comment references its parent article and its author. PostgreSQL refuses dangling references.\n\nDeleting an article cascades to its comments. The comment service still checks the parent article and the actor before changing a comment.", "postgresql", "schema"),
            article("Preserve drafts across sign in", "A session timeout should not discard an article.",
                "A JWT has a finite lifetime. An editor can remain open longer than that lifetime.\n\nThe client keeps a tab-local draft, opens sign in after an authenticated HTTP 401, and returns to the editor. Draft keys include the account and article.", "vue", "security"),
            article("Test against the database you use", "Why the integration suite starts PostgreSQL.",
                "Native SQL, composite keys and locking are part of the application's behavior.\n\nTestcontainers starts PostgreSQL for the backend tests. A different in-memory database cannot establish that PostgreSQL-specific queries behave correctly.", "java", "testing"),
            article("Stable ordering needs a tie breaker", "Two articles can share a creation timestamp.",
                "The article list orders by created_at descending and then by ID descending.\n\nThe ID makes the ordering deterministic for equal timestamps. Offset pagination can still shift when new rows are inserted; it is not a snapshot of the whole feed.", "postgresql", "pagination"),
            article("Sanitize Markdown after rendering", "Rich article text still needs an HTML boundary.",
                "The editor and article page render Markdown to HTML.\n\nAn allowlist sanitizer removes unsupported tags, attributes and unsafe URLs before the result reaches v-html. A preview must use the same sanitizer as the published article.", "vue", "security"),
            article("A batch lookup avoids repeated queries", "Load response metadata for a page together.",
                "An article response includes its author, favorite state and following flag. Fetching each separately for every article grows query counts with page size.\n\nConduit collects IDs and loads this metadata in batches before building response DTOs.", "java", "performance"),
            article("Migrations are recorded changes", "How Flyway and Hibernate divide responsibility.",
                "Flyway applies versioned SQL migrations to build or update the schema.\n\nHibernate validates the resulting tables and columns. An already applied migration stays unchanged; later schema changes receive another migration.", "postgresql", "schema"),
            article("Explore this demonstration", "A short route through articles, favorites and follows.",
                "Create a temporary account, publish an article and add a comment. Favorite another author's article and follow that author.\n\nOpen Your Feed to see followed authors. Use profile favorites and tags to compare filters. This is disposable demo data and can be reset by the operator.", "vue", "demo")
        );
    }

    private static Example article(String title, String description, String body, String... tags) {
        return new Example(title, description, "## " + title + "\n\n" + body, List.of(tags));
    }

    private DemoArticles() {}
}
