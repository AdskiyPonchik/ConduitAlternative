package de.conduit.comments;

import de.conduit.articles.Article;
import de.conduit.articles.ArticleRepository;
import de.conduit.comments.dto.CommentListView;
import de.conduit.comments.dto.CommentView;
import de.conduit.comments.dto.CreateCommentCommand;
import de.conduit.articles.exception.ArticleNotFoundException;
import de.conduit.comments.exception.CommentAccessDeniedException;
import de.conduit.comments.exception.CommentNotFoundException;
import de.conduit.comments.exception.CommentUserNotFoundException;
import de.conduit.users.AuthorProfiles;
import de.conduit.users.AuthorProfiles.AuthorProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefaultCommentService implements CommentService {
    private final ArticleRepository articles;
    private final CommentRepository comments;
    private final AuthorProfiles authors;
    private final Clock clock;

    public DefaultCommentService(
            ArticleRepository articles, CommentRepository comments,
            AuthorProfiles authors, Clock clock
    ) {
        this.articles = articles;
        this.comments = comments;
        this.authors = authors;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CommentView create(UUID actorId, String slug, CreateCommentCommand command) {
        Objects.requireNonNull(command, "command is required");
        AuthorProfile author = requireCurrentUser(actorId);
        Article article = articles.findBySlugForUpdate(slug)
                .orElseThrow(ArticleNotFoundException::new);

        Comment comment = Comment.create(
                article.getId(), actorId, command.body(), Instant.now(clock)
        );
        comments.saveAndFlush(comment);
        return view(comment, author);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentListView list(String slug) {
        Article article = articles.findBySlug(slug)
                .orElseThrow(ArticleNotFoundException::new);
        List<Comment> found = comments.findByArticleIdOrderByCreatedAtDescIdDesc(article.getId());
        Set<UUID> authorIds = found.stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toSet());
        Map<UUID, AuthorProfile> profiles = authors.findByIDs(authorIds);

        List<CommentView> views = found.stream().map(comment -> {
            AuthorProfile author = profiles.get(comment.getAuthorId());
            if (author == null) {
                throw new IllegalStateException("Comment author is missing");
            }
            return view(comment, author);
        }).toList();
        return new CommentListView(views);
    }

    @Override
    @Transactional
    public void delete(UUID actorId, String slug, Integer commentId) {
        requireCurrentUser(actorId);
        Objects.requireNonNull(commentId, "commentId is required");
        Article article = articles.findBySlugForUpdate(slug)
                .orElseThrow(ArticleNotFoundException::new);
        Comment comment = comments.findByIdAndArticleId(commentId, article.getId())
                .orElseThrow(CommentNotFoundException::new);
        if (!comment.isAuthoredBy(actorId)) {
            throw new CommentAccessDeniedException();
        }
        comments.delete(comment);
        comments.flush();
    }

    private AuthorProfile requireCurrentUser(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId is required");
        return authors.findById(actorId).orElseThrow(CommentUserNotFoundException::new);
    }

    private static CommentView view(Comment comment, AuthorProfile author) {
        return new CommentView(
                comment.getId(), comment.getCreatedAt(), comment.getUpdatedAt(), comment.getBody(),
                new CommentView.AuthorView(author.username(), author.bio(), author.image(), false)
        );
    }
}
