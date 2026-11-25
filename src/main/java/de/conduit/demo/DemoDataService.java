package de.conduit.demo;

import de.conduit.articles.Article;
import de.conduit.articles.ArticleFavorites;
import de.conduit.comments.Comment;
import de.conduit.users.User;
import de.conduit.users.UserFollows;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "conduit.demo.enabled", havingValue = "true")
public class DemoDataService {
    private final EntityManager entities;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final ArticleFavorites favorites;
    private final UserFollows follows;
    private final String databaseName;
    private final String password;

    public DemoDataService(EntityManager entities, PasswordEncoder passwords, Clock clock,
                           ArticleFavorites favorites, UserFollows follows,
                           @Value("${conduit.demo.database-name:}") String databaseName,
                           @Value("${conduit.demo.password:}") String password) {
        this.entities = entities;
        this.passwords = passwords;
        this.clock = clock;
        this.favorites = favorites;
        this.follows = follows;
        this.databaseName = databaseName;
        this.password = password;
    }

    @Transactional
    public void initialize(boolean reset) {
        String actual = (String) entities.createNativeQuery("select current_database()", String.class).getSingleResult();
        if (!databaseName.endsWith("_demo") || !actual.equals(databaseName)) {
            throw new IllegalStateException("Demo requires a dedicated database ending in _demo and an exact CONDUIT_DEMO_DATABASE confirmation");
        }
        // Serialize initialization across application instances, within this transaction.
        entities.createNativeQuery("select 1 from pg_advisory_xact_lock(697104582)").getSingleResult();
        boolean initialized = ((Number) entities.createNativeQuery("select count(*) from demo_state", Long.class)
                .getSingleResult()).longValue() > 0;
        if (!initialized && ((Number) entities.createNativeQuery("select count(*) from users", Long.class)
                .getSingleResult()).longValue() > 0) {
            throw new IllegalStateException("Refusing to initialize demo in a database containing unmarked user data");
        }
        if (initialized && !reset) return;
        if (password.codePointCount(0, password.length()) < 15 || password.codePointCount(0, password.length()) > 128) {
            throw new IllegalStateException("CONDUIT_DEMO_PASSWORD must contain 15–128 characters");
        }
        if (reset) {
            for (String table : List.of("comments", "article_favorites", "user_follows", "article_tags", "articles", "users", "demo_state")) {
                entities.createNativeQuery("delete from " + table).executeUpdate();
            }
            entities.clear();
        }
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<String> names = List.of("java-author", "database-author", "vue-author", "demo-reader");
        for (String name : names) {
            User user = User.register(id(name), name, name + "@demo.conduit.test", passwords.encode(password), now);
            user.updateProfile(name, "Sample account for the Conduit demonstration.", "", now);
            entities.persist(user);
        }
        entities.flush();
        var examples = DemoArticles.examples();
        for (int i = 0; i < examples.size(); i++) {
            var example = examples.get(i);
            UUID author = id(names.get(i % 3));
            Article article = Article.create(id("article-" + i), author, example.title(), example.description(),
                    example.body(), example.tags(), now.minus(examples.size() - i, ChronoUnit.DAYS));
            entities.persist(article);
            entities.flush();
            entities.persist(Comment.create(article.getId(), id("demo-reader"),
                    "Try adding your own comment, then compare the article in the global and personal feeds.", now));
            favorites.add(article.getId(), id("demo-reader"));
            if (i % 2 == 0) favorites.add(article.getId(), id(names.get((i + 1) % 3)));
        }
        follows.add(id("demo-reader"), id("java-author"));
        follows.add(id("demo-reader"), id("database-author"));
        entities.createNativeQuery("insert into demo_state (id, dataset_version, initialized_at) values (1, 1, :now)")
                .setParameter("now", now).executeUpdate();
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("conduit-demo-v1:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
