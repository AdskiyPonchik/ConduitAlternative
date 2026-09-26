CREATE TABLE article_favorites (
                                   article_id UUID NOT NULL,
                                   user_id UUID NOT NULL,
                                   PRIMARY KEY (article_id, user_id),
                                   CONSTRAINT fk_article_favorites_article
                                       FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
                                   CONSTRAINT fk_article_favorites_user
                                       FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX ix_article_favorites_user_id ON article_favorites(user_id);