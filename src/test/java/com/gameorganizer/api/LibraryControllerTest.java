package com.gameorganizer.api;

import com.gameorganizer.domain.model.UserGame;
import com.gameorganizer.domain.repository.UserGameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@WebFluxTest(LibraryController.class)
class LibraryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserGameRepository repository;

    @Test
    @DisplayName("Should add game to library if not exists")
    void shouldAddGameToLibrary() {
        UserGame newGame = new UserGame();
        newGame.setRawgId(100);
        newGame.setTitle("Test Game");
        when(repository.findByRawgId(100)).thenReturn(Mono.empty());
        when(repository.save(any(UserGame.class))).thenReturn(Mono.just(newGame));

        webTestClient.post()
                .uri("/api/library")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(newGame)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserGame.class)
                .value(g -> assertEquals("Test Game", g.getTitle()));

        verify(repository, times(1)).save(any(UserGame.class));
    }

    @Test
    @DisplayName("Should update list order based on IDs received")
    void shouldReorderLibrary() {
        List<Long> idOrder = List.of(10L, 5L, 2L);

        UserGame game1 = new UserGame(); game1.setId(10L);
        UserGame game2 = new UserGame(); game2.setId(5L);
        UserGame game3 = new UserGame(); game3.setId(2L);

        when(repository.findById(10L)).thenReturn(Mono.just(game1));
        when(repository.findById(5L)).thenReturn(Mono.just(game2));
        when(repository.findById(2L)).thenReturn(Mono.just(game3));

        when(repository.save(any(UserGame.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

        webTestClient.post()
                .uri("/api/library/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(idOrder)
                .exchange()
                .expectStatus().isOk();
        verify(repository).save(argThat(g -> g.getId().equals(10L) && g.getListOrder() == 0));
        verify(repository).save(argThat(g -> g.getId().equals(5L) && g.getListOrder() == 1));
        verify(repository).save(argThat(g -> g.getId().equals(2L) && g.getListOrder() == 2));
    }
}