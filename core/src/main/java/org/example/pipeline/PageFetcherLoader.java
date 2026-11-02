package org.example.pipeline;

import org.example.ai.AiConfig;

import java.io.IOException;
import java.util.logging.Logger;

public class PageFetcherLoader implements PageLoader {

    private static final Logger log = Logger.getLogger(PageFetcherLoader.class.getName());

    private final PageFetcher fetcher;

    public PageFetcherLoader() {
        this(new PageFetcher(
                AiConfig.getFetchTimeoutSeconds(),
                AiConfig.getFetchMaxBytes(),
                AiConfig.getFetchMaxRedirects()
        ));
    }

    PageFetcherLoader(PageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public Result load(String url) throws SsrfViolationException {
        try {
            FetchResult fr = fetcher.fetch(url);
            ParseResult parsed = PageParser.parse(fr.html(), fr.finalUrl());
            return new Result(parsed, fr.finalUrl());
        } catch (PageFetcher.SsrfException e) {
            throw new SsrfViolationException(e.getMessage());
        } catch (IOException e) {
            log.warning("PageFetcherLoader: fetch failed for " + url + " — " + e.getMessage());
            return null;
        }
    }
}
