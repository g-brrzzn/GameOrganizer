package com.gameorganizer.domain;

import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameEnrichmentPort;
import com.gameorganizer.domain.port.GameSearchPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameSearchPort searchPort;

    @Mock
    private GameEnrichmentPort enrichmentPort;

    @InjectMocks
    private GameService gameService;

    @Test
    @DisplayName("Should orchestrate search and enrichment correctly")
    void shouldOrganizeGameByName() {
        String query = "Witcher";

        Game rawGame = new Game(1, "The Witcher", 95, "2015", "img.jpg", List.of("RPG"), null, 123);
        when(searchPort.searchGamesByName(query))
                .thenReturn(Flux.just(rawGame));
        when(enrichmentPort.enrich(any(GameResponseDto.class), any(Game.class)))
                .thenAnswer(invocation -> {
                    GameResponseDto dto = invocation.getArgument(0);
                    dto.setSteamUrl("https://steam.../123");
                    return Mono.just(dto);
                });
        Flux<GameResponseDto> result = gameService.organizeByNameReactive(query);
        StepVerifier.create(result)
                .expectNextMatches(dto ->
                        dto.getName().equals("The Witcher") &&
                                dto.getMetacritic() == 95 &&
                                dto.getSteamUrl().equals("https://steam.../123")
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty flux when search yields no results")
    void shouldReturnEmptyWhenNoResults() {
        when(searchPort.searchGamesByName("Unknown"))
                .thenReturn(Flux.empty());

        Flux<GameResponseDto> result = gameService.organizeByNameReactive("Unknown");

        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();
    }
}