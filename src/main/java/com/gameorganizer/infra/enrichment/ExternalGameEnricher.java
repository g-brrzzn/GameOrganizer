package com.gameorganizer.infra.enrichment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameEnrichmentPort;
import com.gameorganizer.infra.client.howlongtobeat.HltbScraperAdapter;
import com.gameorganizer.infra.client.howlongtobeat.dto.HLTBInfo;
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
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Optional;

@Service
@Profile("!mock-enrichment")
public class ExternalGameEnricher implements GameEnrichmentPort {

    private static final Logger log = LoggerFactory.getLogger(ExternalGameEnricher.class);
    private static final String STEAM_API_URL = "https://store.steampowered.com/api/appdetails";

    private final HltbScraperAdapter hltbService;
    private final WebClient webClient;

    public ExternalGameEnricher(HltbScraperAdapter hltbService, WebClient webClient) {
        this.hltbService = hltbService;
        this.webClient = webClient;
    }

    @Override
    public Mono<GameResponseDto> enrich(GameResponseDto overview, Game game) {
        Mono<Optional<SteamAppDetailsData>> steamDetailsMono = Mono.justOrEmpty(overview.getSteamAppid())
                .flatMap(this::fetchSteamAppDetails)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.error("Falha ao buscar detalhes da Steam: {}", e.getMessage());
                    return Mono.just(Optional.empty());
                });

        Mono<Optional<HLTBInfo>> hltbInfoMono = Mono.fromCallable(() -> hltbService.search(game.name()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Falha ao fazer scraping do HLTB: {}", e.getMessage());
                    return Mono.just(Optional.empty());
                });

        return Mono.zip(steamDetailsMono, hltbInfoMono)
                .map(tuple -> {
                    Optional<SteamAppDetailsData> steamDataOpt = tuple.getT1();
                    Optional<HLTBInfo> hltbInfoOpt = tuple.getT2();

                    steamDataOpt.ifPresent(steamData -> {
                        SteamAppDetails details = new SteamAppDetails();
                        details.setRaw(steamData);
                        overview.setSteamData(details);
                    });

                    hltbInfoOpt.ifPresent(info -> {
                        overview.setHltbMain(info.getMain());
                        overview.setHltbCompletionist(info.getCompletionist());
                        overview.setHltbUrl(info.getUrl());
                    });
                    return overview;
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