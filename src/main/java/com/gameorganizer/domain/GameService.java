package com.gameorganizer.domain;

import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameEnrichmentPort;
import com.gameorganizer.domain.port.GameSearchPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class GameService {

    private final GameSearchPort searchPort;
    private final GameEnrichmentPort enrichmentPort;

    public GameService(GameSearchPort searchPort, GameEnrichmentPort enrichmentPort) {
        this.searchPort = searchPort;
        this.enrichmentPort = enrichmentPort;
    }

    public Flux<GameResponseDto> organizeByNameReactive(String name) {
        return searchPort.searchGamesByName(name)
                .flatMap(this::enrichGameData);
    }

    private Mono<GameResponseDto> enrichGameData(Game game) {
        GameResponseDto responseDto = mapGameToResponseDto(game);
        return enrichmentPort.enrich(responseDto, game);
    }

    private GameResponseDto mapGameToResponseDto(Game game) {
        GameResponseDto dto = new GameResponseDto();
        dto.setRawgId(game.id());
        dto.setName(game.name());
        dto.setPlaytime(game.playtime());
        dto.setBackgroundImage(game.backgroundImage());
        dto.setGenres(game.genres());
        dto.setSteamUrl(game.steamUrl());
        dto.setSteamAppid(game.steamAppId());
        return dto;
    }
}