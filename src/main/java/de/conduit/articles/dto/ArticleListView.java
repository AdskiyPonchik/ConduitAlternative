package de.conduit.articles.dto;

import java.util.List;

public record ArticleListView(List<ArticleView> articles, long articlesCount) {
    public ArticleListView {
        articles = List.copyOf(articles);
    }
}
