package org.example.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Данные статьи, извлечённые AI из URL.
 * Jackson автоматически заполняет поля из JSON-ответа Llama.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedArticle {

    @JsonProperty("title")
    private String title;

    @JsonProperty("url")
    private String url;

    @JsonProperty("language")
    private String language;

    @JsonProperty("region")
    private String region;

    @JsonProperty("publication")
    private String publication;

    @JsonProperty("tags")
    private List<TagEntry> tags;

    // ── Вложенный класс для тега ──
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TagEntry {
        @JsonProperty("name")
        private String name;

        @JsonProperty("percent")
        private int percent;

        public TagEntry() {}

        public TagEntry(String name, int percent) {
            this.name    = name;
            this.percent = percent;
        }

        public String getName()    { return name; }
        public int    getPercent() { return percent; }

        @Override
        public String toString() { return name + "(" + percent + "%)"; }
    }

    public void setTitle(String title)               { this.title       = title; }
    public void setUrl(String url)                   { this.url         = url; }
    public void setTags(List<TagEntry> tags)         { this.tags        = tags; }
    public void setLanguage(String language)         { this.language    = language; }
    public void setRegion(String region)             { this.region      = region; }
    public void setPublication(String publication)   { this.publication = publication; }

    public String          getTitle()       { return title; }
    public String          getUrl()         { return url; }
    public String          getLanguage()    { return language; }
    public String          getRegion()      { return region; }
    public String          getPublication() { return publication; }
    public List<TagEntry>  getTags()        { return tags; }

    @Override
    public String toString() {
        return "ExtractedArticle{title='" + title + "', url='" + url
                 + "', tags=" + tags + "}";
    }
}
