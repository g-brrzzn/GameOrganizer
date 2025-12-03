package com.gameorganizer.infra.client.rawg;

import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameSearchPort;
import com.gameorganizer.infra.client.rawg.dto.RawgResult;
import com.gameorganizer.infra.client.rawg.dto.RawgSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Profile("!mock-search")
public class RawgAdapter implements GameSearchPort {

    private static final Logger log = LoggerFactory.getLogger(RawgAdapter.class);
    private static final String RAWG_API_URL = "https://api.rawg.io/api/games";
    private static final Pattern STEAM_APP_PATTERN = Pattern.compile("store.steampowered.com/app/(\\d+)");

    private final WebClient webClient;
    private final String rawgApiKey;

    public RawgAdapter(WebClient webClient, @Value("${rawg.api.key:}") String rawgApiKey) {
        this.webClient = webClient;
        this.rawgApiKey = rawgApiKey;
    }

    @Override
    public Flux<Game> searchGamesByName(String name) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(RAWG_API_URL)
                .queryParam("search", urlEncode(name))
                .queryParam("page_size", 15);

        if (rawgApiKey != null && !rawgApiKey.isEmpty()) {
            uriBuilder.queryParam("key", rawgApiKey);
        }

        return webClient.get()
                .uri(uriBuilder.toUriString())
                .retrieve()
                .bodyToMono(RawgSearchResponse.class)
                .flatMapMany(response -> Flux.fromIterable(
                        Optional.ofNullable(response.results).orElse(Collections.emptyList())
                ))
                .map(this::toDomainModel)
                .onErrorResume(e -> {
                    log.error("Erro ao chamar a API da RAWG para '{}': {}", name, e.getMessage());
                    return Flux.empty();
                });
    }

    private Game toDomainModel(RawgResult rawgResult) {
        List<String> genres = Optional.ofNullable(rawgResult.genres).orElse(Collections.emptyList())
                .stream()
                .map(genre -> genre.name)
                .toList();

        String steamUrl = Optional.ofNullable(rawgResult.stores).orElse(Collections.emptyList())
                .stream()
                .filter(s -> s != null && s.store != null && "steam".equalsIgnoreCase(s.store.name))
                .findFirst()
                .map(s -> s.url)
                .orElse(null);

        Integer steamAppId = extractSteamAppId(rawgResult);
        String year = extractYear(rawgResult.released);

        return new Game(
                rawgResult.id,
                rawgResult.name,
                rawgResult.metacritic,
                year,
                rawgResult.background_image,
                genres,
                steamUrl,
                steamAppId
        );
    }

    private String extractYear(String dateStr) {
        if (dateStr == null || dateStr.length() < 4) return "TBA";
        return dateStr.substring(0, 4);
    }

    private Integer extractSteamAppId(RawgResult r) {
        return Optional.ofNullable(r.stores).orElse(Collections.emptyList())
                .stream()
                .filter(store -> store != null && store.url != null)
                .map(store -> STEAM_APP_PATTERN.matcher(store.url))
                .filter(Matcher::find)
                .findFirst()
                .map(matcher -> {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private static String urlEncode(String s) {
        if (s == null || s.isEmpty()) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}