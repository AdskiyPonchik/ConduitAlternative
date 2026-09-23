CREATE TABLE articles (
                          id UUID PRIMARY KEY,
                          author_id UUID NOT NULL,
                          slug VARCHAR(120) NOT NULL,
                          title VARCHAR(200) NOT NULL,
                          description VARCHAR(500) NOT NULL,
                          body TEXT NOT NULL,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          version BIGINT NOT NULL DEFAULT 0,

                          CONSTRAINT fk_articles_author
                              FOREIGN KEY (author_id) REFERENCES users(id),
                          CONSTRAINT ux_articles_slug UNIQUE (slug),
                          CONSTRAINT ck_articles_slug_not_blank CHECK (btrim(slug) <> ''),
                          CONSTRAINT ck_articles_title_not_blank CHECK (btrim(title) <> ''),
                          CONSTRAINT ck_articles_description_not_blank
                              CHECK (btrim(description) <> ''),
                          CONSTRAINT ck_articles_body
                              CHECK (btrim(body) <> '' AND char_length(body) <= 100000)
);

CREATE INDEX ix_articles_author_id ON articles(author_id);

CREATE TABLE article_tags (
                              article_id UUID NOT NULL,
                              tag VARCHAR(50) NOT NULL,

                              PRIMARY KEY (article_id, tag),
                              CONSTRAINT fk_article_tags_article
                                  FOREIGN KEY (article_id) REFERENCES articles(id) ON DELETE CASCADE,
                              CONSTRAINT ck_article_tags_not_blank CHECK (btrim(tag) <> '')
);

CREATE INDEX ix_article_tags_tag ON article_tags(tag);