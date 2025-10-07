package com.gameorganizer.domain.port;

import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import reactor.core.publisher.Mono;

public interface GameEnrichmentPort {
    Mono<GameResponseDto> enrich(GameResponseDto overview, Game game);
}