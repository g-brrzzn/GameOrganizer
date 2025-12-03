package com.gameorganizer.infra.enrichment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameEnrichmentPort;
import com.gameorganizer.infra.client.steam.dto.SteamAppDetails;
import com.gameorganizer.infra.client.steam.dto.SteamAppDetailsData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@Service
@Profile("!mock-enrichment")
public class ExternalGameEnricher implements GameEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(ExternalGameEnricher.class);
    private static final String STEAM_API_URL = "https://store.steampowered.com/api/appdetails";

    private final WebClient webClient;

    public ExternalGameEnricher(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<GameResponseDto> enrich(GameResponseDto overview, Game game) {
        if (overview.getSteamAppid() == null) {
            return Mono.just(overview);
        }

        return fetchSteamAppDetails(overview.getSteamAppid())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .map(steamDataOpt -> {
                    steamDataOpt.ifPresent(steamData -> {
                        SteamAppDetails details = new SteamAppDetails();
                        details.setRaw(steamData);
                        overview.setSteamData(details);
                    });
                    return overview;
                })
                .onErrorResume(e -> {
                    log.error("Falha ao buscar detalhes da Steam: {}", e.getMessage());
                    return Mono.just(overview);
                });
    }

    private Mono<SteamAppDetailsData> fetchSteamAppDetails(int appid) {
        String url = UriComponentsBuilder.fromHttpUrl(STEAM_API_URL)
                .queryParam("appids", appid)
                .queryParam("l", "en")
                .queryParam("cc", "us")
                .toUriString();
        var responseType = new ParameterizedTypeReference<Map<String, SteamAppDataContainer>>() {};

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType)
                .mapNotNull(responseMap -> responseMap.get(String.valueOf(appid)))
                .filter(container -> container.success)
                .map(container -> container.data);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SteamAppDataContainer {
        @JsonProperty("success") public boolean success;
        @JsonProperty("data") public SteamAppDetailsData data;
    }
}