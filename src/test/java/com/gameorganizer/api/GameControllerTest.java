package com.gameorganizer.api;

import com.gameorganizer.api.dto.GameResponseDto;
import com.gameorganizer.domain.GameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@WebFluxTest(GameController.class)
class GameControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GameService gameService;

    @Test
    @DisplayName("Should return sorted list of games by Metacritic score")
    void shouldReturnSortedGames() {
        GameResponseDto game1 = new GameResponseDto();
        game1.setName("Bad Game");
        game1.setMetacritic(50);

        GameResponseDto game2 = new GameResponseDto();
        game2.setName("Good Game");
        game2.setMetacritic(90);
        given(gameService.organizeByNameReactive("game"))
                .willReturn(Flux.just(game1, game2));
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/games/organize")
                        .queryParam("name", "game")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GameResponseDto.class)
                .value(list -> {
                    assertEquals("Good Game", list.get(0).getName());
                    assertEquals("Bad Game", list.get(1).getName());
                });
    }
}