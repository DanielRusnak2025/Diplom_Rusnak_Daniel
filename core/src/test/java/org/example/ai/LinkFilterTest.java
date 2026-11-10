package org.example.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinkFilterTest {

    private final LinkFilter filter = new LinkFilter();

    @Test
    void dw_topic_path_rejected() {
        assertFalse(filter.isLikelyArticle("https://www.dw.com/en/t-12345678/society"));
    }

    @Test
    void dw_section_path_rejected() {
        assertFalse(filter.isLikelyArticle("https://www.dw.com/en/s-9999/technology"));
    }

    @Test
    void dw_article_path_allowed() {
        assertTrue(filter.isLikelyArticle("https://www.dw.com/en/germany-election-results/a-67890"));
    }

    @Test
    void non_dw_site_with_t_segment_allowed() {
        assertTrue(filter.isLikelyArticle("https://example.com/en/t-12345/article"));
    }

    @Test
    void dw_with_lang_prefix_and_topic_rejected() {
        assertFalse(filter.isLikelyArticle("https://dw.com/ru/t-54321"));
    }

    @Test
    void dw_bare_domain_section_rejected() {
        assertFalse(filter.isLikelyArticle("https://dw.com/s-100/politics"));
    }
}
