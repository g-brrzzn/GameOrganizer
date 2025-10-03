package com.gameorganizer.service;

import com.gameorganizer.service.hltb.HLTBInfo;
import com.gameorganizer.service.hltb.HowLongToBeatService;
import com.gameorganizer.service.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GameOrganizerService {

    private static final Logger log = LoggerFactory.getLogger(GameOrganizerService.class);

    private final WebClient webClient;
    private final String rawgApiKey;
    private final Pattern steamAppPattern = Pattern.compile("store.steampowered.com/app/(\\d+)");

    // O HLTB scraper realiza I/O bloqueante e será tratado de forma apropriada.
    private final HowLongToBeatService hltbService = new HowLongToBeatService();

    public GameOrganizerService(WebClient webClient, @Value("${rawg.api.key:}") String rawgApiKey) {
        this.webClient = webClient;
        this.rawgApiKey = rawgApiKey;
    }

    /**
     * Organiza informações de jogos usando um pipeline reativo e não-bloqueante.
     *
     * @param name O nome do jogo a ser pesquisado.
     * @return Um Flux de GameOverview, com cada objeto sendo enriquecido com dados de múltiplas fontes.
     */
    public Flux<GameOverview> organizeByNameReactive(String name) {
        // 1. Busca na RAWG por uma lista de jogos candidatos. Retorna um Flux<RawgResult>.
        return searchRawgReactive(name)
                // 2. Para cada jogo, dispara o processo de enriquecimento em paralelo.
                //    flatMap é ideal para concorrência, transformando cada RawgResult em um Mono<GameOverview>.
                .flatMap(this::enrichGameData);
    }

    /**
     * Enriquece um único RawgResult com dados da Steam e HowLongToBeat.
     *
     * @param rawgResult Os dados iniciais do jogo vindos da RAWG.
     * @return Um Mono que emitirá o GameOverview totalmente enriquecido.
     */
    private Mono<GameOverview> enrichGameData(RawgResult rawgResult) {
        // Cria o objeto GameOverview base a partir dos dados da RAWG.
        GameOverview overview = mapRawgResultToGameOverview(rawgResult);

        // Cria um pipeline reativo para buscar dados da Steam.
        // Retorna um Mono vazio se não encontrar appid ou se a busca falhar.
        Mono<Void> steamEnrichment = Mono.justOrEmpty(overview.getSteamAppid())
                .flatMap(this::fetchSteamAppDetailsReactive)
                .doOnNext(overview::setSteamData)
                .onErrorResume(e -> {
                    log.error("Falha ao buscar detalhes da Steam para o RAWG ID {}: {}", overview.getRawgId(), e.getMessage());
                    return Mono.empty();
                })
                .then(); // Converte para Mono<Void> pois só nos interessa o efeito colateral (doOnNext).

        // Cria um pipeline para buscar dados do HLTB.
        // Como o hltbService é bloqueante, envolvemos a chamada em fromCallable e a executamos em um scheduler apropriado.
        Mono<Void> hltbEnrichment = Mono.fromCallable(() -> hltbService.search(rawgResult.name))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(optionalHltb -> optionalHltb.ifPresent(info -> {
                    overview.setHltbMain(info.getMain());
                    overview.setHltbCompletionist(info.getCompletionist());
                    overview.setHltbUrl(info.getUrl());
                }))
                .onErrorResume(e -> {
                    log.error("Falha ao fazer scraping do HLTB para o jogo '{}': {}", rawgResult.name, e.getMessage());
                    return Mono.empty();
                })
                .then();

        // 3. Executa ambos os pipelines de enriquecimento (Steam e HLTB) em paralelo.
        //    Mono.when() aguarda a conclusão de todos os Monos fornecidos.
        // 4. thenReturn() emite o objeto 'overview' (que foi modificado) assim que tudo terminar.
        return Mono.when(steamEnrichment, hltbEnrichment)
                .thenReturn(overview);
    }

    /**
     * Mapeia o resultado inicial da busca na RAWG para o nosso objeto de domínio GameOverview.
     */
    private GameOverview mapRawgResultToGameOverview(RawgResult r) {
        GameOverview g = new GameOverview();
        g.setName(r.name);
        g.setRawgId(r.id);
        g.setPlaytime(r.playtime);
        g.setBackgroundImage(r.background_image);
        g.setSteamAppid(extractSteamAppId(r));

        if (r.genres != null) {
            g.setGenres(r.genres.stream().map(genre -> genre.name).collect(Collectors.toList()));
        } else {
            g.setGenres(Collections.emptyList());
        }

        if (r.stores != null) {
            r.stores.stream()
                    .filter(s -> s != null && s.store != null && s.store.name != null && "steam".equalsIgnoreCase(s.store.name.trim()))
                    .findFirst()
                    .ifPresent(s -> g.setSteamUrl(s.url));
        }
        return g;
    }

    /**
     * Busca jogos na API da RAWG de forma reativa.
     *
     * @param name O termo da busca.
     * @return Um Flux emitindo cada resultado de jogo encontrado.
     */
    private Flux<RawgResult> searchRawgReactive(String name) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl("https://api.rawg.io/api/games")
                .queryParam("search", urlEncode(name))
                .queryParam("page_size", 15);

        if (rawgApiKey != null && !rawgApiKey.isEmpty()) {
            uriBuilder.queryParam("key", rawgApiKey);
        }

        return webClient.get()
                .uri(uriBuilder.toUriString())
                .retrieve()
                .bodyToMono(RawgSearchResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.results != null ? response.results : Collections.emptyList()))
                .onErrorResume(e -> {
                    log.error("Erro ao chamar a API da RAWG para o termo de busca '{}': {}", name, e.getMessage());
                    return Flux.empty(); // Retorna um fluxo vazio em caso de erro.
                });
    }

    /**
     * Busca detalhes de um aplicativo na API da Steam de forma reativa.
     *
     * @param appid O ID do aplicativo na Steam.
     * @return Um Mono emitindo os SteamAppDetails.
     */
    private Mono<SteamAppDetails> fetchSteamAppDetailsReactive(int appid) {
        String url = "https://store.steampowered.com/api/appdetails?appids=" + appid + "&l=en&cc=us";

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class) // A resposta da API é um mapa cuja chave é o appid
                .map(responseMap -> {
                    SteamAppDetails details = new SteamAppDetails();
                    details.setRaw(responseMap.get(String.valueOf(appid)));
                    return details;
                })
                .onErrorResume(e -> {
                    log.error("Erro ao chamar a API da Steam para o appid {}: {}", appid, e.getMessage());
                    return Mono.empty(); // Retorna um Mono vazio em caso de erro.
                });
    }

    /**
     * Extrai o App ID da Steam a partir das URLs de lojas de um resultado da RAWG.
     */
    private Integer extractSteamAppId(RawgResult r) {
        if (r.stores == null) return null;

        for (RawgStore s : r.stores) {
            if (s != null && s.url != null) {
                Matcher m = steamAppPattern.matcher(s.url);
                if (m.find()) {
                    try {
                        return Integer.parseInt(m.group(1));
                    } catch (NumberFormatException e) {
                        log.warn("Não foi possível parsear o App ID da Steam da URL: {}", s.url);
                        // Continua para a próxima loja
                    }
                }
            }
        }
        return null;
    }

    /**
     * Utilitário para codificar uma string para ser usada em uma URL.
     */
    private static String urlEncode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            log.error("Falha ao codificar URL para a string: {}", s, e);
            return "";
        }
    }
}