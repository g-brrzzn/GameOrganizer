package com.gameorganizer.infra.client.howlongtobeat;

import com.gameorganizer.infra.client.howlongtobeat.dto.HLTBInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HltbScraperAdapter {

    private static final Logger log = LoggerFactory.getLogger(HltbScraperAdapter.class);
    private static final String HLTB_SEARCH_URL = "https://howlongtobeat.com/search_results";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    private final WebClient webClient;
    private final Cache<String, Optional<HLTBInfo>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(24))
            .maximumSize(1000)
            .build();

    public HltbScraperAdapter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public Mono<Optional<HLTBInfo>> search(String gameTitle) {
        if (gameTitle == null || gameTitle.isBlank()) return Mono.just(Optional.empty());
        String key = gameTitle.trim().toLowerCase();
        Optional<HLTBInfo> cached = cache.getIfPresent(key);
        if (cached != null) return Mono.just(cached);
        return fetchFromHltbReactive(gameTitle)
                .doOnNext(opt -> cache.put(key, opt));
    }

    private Mono<Optional<HLTBInfo>> fetchFromHltbReactive(String query) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("howlongtobeat.com")
                        .path("/")
                        .queryParam("q", query)
                        .build())
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://howlongtobeat.com/")
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parseHtmlResponse(query, html))
                .onErrorResume(e -> {

                    log.warn("HLTB Scraper falhou (Site mudou ou bloqueou): {} - Usando Fallback da RAWG", e.getMessage());
                    return Mono.just(Optional.empty());
                });
    }
    private Optional<HLTBInfo> parseHtmlResponse(String query, String html) {
        try {
            String text = Jsoup.parse(html).text();

            Double main = extractTimeForLabel(text, "(Main Story|Main)[\\s\\S]{0,40}?([0-9½\\.\\-–, ]+?)\\s*(Hours|hrs|hour|hr)");
            Double completion = extractTimeForLabel(text, "(Completionist|Completionist Time|Completionists)[\\s\\S]{0,60}?([0-9½\\.\\-–, ]+?)\\s*(Hours|hrs|hour|hr)");
            if (main == null) {
                Double mainExtra = extractTimeForLabel(text, "(Main \\+ Extra|Main + Extra)[\\s\\S]{0,60}?([0-9½\\.\\-–, ]+?)\\s*(Hours|hrs|hour|hr)");
                if (mainExtra != null) main = mainExtra;
            }

            if (main == null && completion == null) return Optional.empty();

            HLTBInfo info = new HLTBInfo();
            info.setName(query);
            if (main != null) info.setMain(formatHours(main));
            if (completion != null) info.setCompletionist(formatHours(completion));
            info.setUrl("https://howlongtobeat.com/?q=" + query.replace(" ", "+"));

            return Optional.of(info);
        } catch (Exception e) {
            log.error("Erro ao parsear resposta HLTB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String formatHours(Double hours) {
        if (hours == null) return null;
        if (hours == Math.floor(hours)) {
            return String.format("%.0f Hours", hours);
        }
        return String.format("%.1f Hours", hours);
    }

    private Double extractTimeForLabel(String text, String labelRegex) {
        Pattern p = Pattern.compile(labelRegex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String group = m.group(2);
            if (group == null) return null;
            String cleaned = group.replace("hours", "").replace("hr", "")
                    .replaceAll("[^0-9½\\.,\\-– ]", "").trim();

            cleaned = cleaned.replace(',', '.').replace('\u00BD', '½');

            if (cleaned.contains("-") || cleaned.contains("–")) {
                String s = cleaned.replace("–", "-");
                String[] parts = s.split("-");
                try {
                    double a = parsePossiblyHalf(parts[0].trim());
                    double b = parsePossiblyHalf(parts[1].trim());
                    return (a + b) / 2.0;
                } catch (Exception ex) {
                    return null;
                }
            } else {
                try {
                    return parsePossiblyHalf(cleaned);
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    private double parsePossiblyHalf(String s) {
        s = s.trim();
        if (s.isEmpty()) throw new NumberFormatException();
        if (s.contains("½")) {
            s = s.replace("½", ".5");
        } else if (s.matches(".*\\b1/2\\b.*")) {
            s = s.replace("1/2", ".5");
        }
        s = s.replaceAll("\\s+", "");
        return Double.parseDouble(s);
    }
}