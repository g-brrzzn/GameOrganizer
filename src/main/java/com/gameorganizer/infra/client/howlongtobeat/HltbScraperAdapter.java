package com.gameorganizer.infra.client.howlongtobeat;

import com.gameorganizer.infra.client.howlongtobeat.dto.HLTBInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class HltbScraperAdapter {

    private static final String BASE = "https://howlongtobeat.com";
    private static final int TIMEOUT = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

    private final Cache<String, Optional<HLTBInfo>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(5_000)
            .build();

    private static final Pattern TIME_PATTERN = Pattern.compile("(?i)(\\d{1,3}(?:[\\.,]\\d+)?(?:½)?\\s*(?:hours|hour|hrs|hr|h))");

    
    public Optional<HLTBInfo> search(String gameTitle) {
        if (gameTitle == null || gameTitle.isBlank()) return Optional.empty();
        String key = gameTitle.trim().toLowerCase();

        Optional<HLTBInfo> cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        Optional<HLTBInfo> result = fetchAndParse(gameTitle);
        cache.put(key, result);
        return result;
    }

    private Optional<HLTBInfo> fetchAndParse(String gameTitle) {
        try {
            String encoded = URLEncoder.encode(gameTitle, StandardCharsets.UTF_8);

            List<String> candidates = List.of(
                    BASE + "/search_results?page=1&searchterm=" + encoded,
                    BASE + "/search_results?page=1&searchterm=" + encoded.replace("+", "%20"),
                    BASE + "/?q=" + encoded,
                    BASE + "/search?q=" + encoded
            );

            Document doc = null;
            Connection.Response lastResp = null;

            for (String url : candidates) {
                try {
                    Connection conn = Jsoup.connect(url)
                            .userAgent(USER_AGENT)
                            .referrer("https://google.com")
                            .timeout(TIMEOUT)
                            .ignoreHttpErrors(true)
                            .followRedirects(true);

                    Connection.Response resp = conn.execute();
                    lastResp = resp;
                    if (resp.statusCode() == 200) {
                        doc = resp.parse();
                        break;
                    }
                } catch (Exception ignore) {

                }
            }

            if (doc == null) return Optional.empty();

            Element firstResult = null;
            Elements results = doc.select(".search_list_details, .search_list_box");
            if (!results.isEmpty()) firstResult = results.first();

            Document detailDoc = doc;
            String nameFound = null;
            if (firstResult != null) {
                Element link = firstResult.selectFirst("a[href]");
                if (link != null) {
                    nameFound = link.text().trim();
                    String href = link.attr("href");
                    String detailUrl = href.startsWith("http") ? href : BASE + href;
                    try {
                        Connection.Response r2 = Jsoup.connect(detailUrl)
                                .userAgent(USER_AGENT)
                                .referrer(BASE)
                                .timeout(TIMEOUT)
                                .ignoreHttpErrors(true)
                                .followRedirects(true)
                                .execute();
                        if (r2.statusCode() == 200) detailDoc = r2.parse();
                    } catch (Exception ignore) {  }
                } else {
                    nameFound = firstResult.text().trim();
                }
            } else {

                Element titleEl = doc.selectFirst("h1, .profile_title, .game_title");
                if (titleEl != null) nameFound = titleEl.text().trim();
            }

            Element mainArea = firstNonNull(
                    detailDoc.selectFirst("#main_content"),
                    detailDoc.selectFirst(".game_page"),
                    detailDoc.selectFirst(".profile"),
                    detailDoc.selectFirst(".content"),
                    detailDoc.selectFirst("body")
            );

            if (mainArea == null) mainArea = detailDoc.body();

            String mainTime = extractTimeByLabel(mainArea, "Main Story");
            if (mainTime == null) mainTime = extractTimeByLabel(mainArea, "Main");

            String completionistTime = extractTimeByLabel(mainArea, "Completionist");

            if (mainTime == null || completionistTime == null) {

                Elements candidatesEls = mainArea.select("div, span, p, li");
                for (Element el : candidatesEls) {
                    String text = el.ownText(); // ownText() evita concatenar texto de filhos grandes
                    if (text == null || text.isBlank()) continue;
                    String lower = text.toLowerCase();
                    if (mainTime == null && (lower.contains("main story") || lower.matches(".*\\bmain\\b.*"))) {
                        String t = findTimeInString(text);
                        if (t != null) mainTime = t;
                    }
                    if (completionistTime == null && lower.contains("completionist")) {
                        String t = findTimeInString(text);
                        if (t != null) completionistTime = t;
                    }
                    if (mainTime != null && completionistTime != null) break;
                }
            }

            if ((mainTime == null || completionistTime == null)) {
                String smallHtml = mainArea.html();

                if (smallHtml.length() > 20_000) smallHtml = smallHtml.substring(0, 20_000);
                Matcher m = TIME_PATTERN.matcher(smallHtml);
                if (m.find() && mainTime == null) mainTime = m.group(1);
                if (m.find() && completionistTime == null) completionistTime = m.group(1);
            }

            HLTBInfo info = new HLTBInfo();
            info.setName(nameFound);
            info.setMain(mainTime);
            info.setCompletionist(completionistTime);

            String foundUrl = null;
            try { foundUrl = detailDoc.baseUri(); } catch (Exception ignored) {}
            if (foundUrl == null && lastResp != null) {
                try { foundUrl = lastResp.url().toString(); } catch (Exception ignored) {}
            }
            info.setUrl(foundUrl);

            if (info.getMain() != null && info.getMain().length() > 300) info.setMain(info.getMain().substring(0, 200).trim());
            if (info.getCompletionist() != null && info.getCompletionist().length() > 300) info.setCompletionist(info.getCompletionist().substring(0, 200).trim());

            return Optional.of(info);

        } catch (Exception e) {

            return Optional.empty();
        }
    }

    private String extractTimeByLabel(Element root, String label) {
        if (label == null || root == null) return null;
        String css = String.format("*:matchesOwn((?i)%s)", Pattern.quote(label)); // matchesOwn para textos curtos
        Elements els = root.select(css);
        for (Element el : els) {

            Element sib = el.nextElementSibling();
            if (sib != null) {
                String t = findTimeInString(sib.ownText());
                if (t != null) return t;
            }

            Element parent = el.parent();
            if (parent != null) {

                Elements candidates = parent.select("span, div, p, li");
                for (Element c : candidates) {
                    String t = findTimeInString(c.ownText());
                    if (t != null) return t;
                }
            }

            String t = findTimeInString(el.ownText());
            if (t != null) return t;
        }
        return null;
    }

    private String findTimeInString(String s) {
        if (s == null || s.isBlank()) return null;
        Matcher m = TIME_PATTERN.matcher(s);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... items) {
        for (T t : items) if (t != null) return t;
        return null;
    }
}
