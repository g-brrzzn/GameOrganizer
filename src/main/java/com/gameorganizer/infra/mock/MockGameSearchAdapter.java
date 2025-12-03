package com.gameorganizer.infra.mock;

import com.gameorganizer.domain.model.Game;
import com.gameorganizer.domain.port.GameSearchPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Profile("mock-search")
public class MockGameSearchAdapter implements GameSearchPort {

    @Override
    public Flux<Game> searchGamesByName(String name) {
        System.out.println("--- USANDO MODO MOCK PARA BUSCA DE JOGOS ---");

        Game game1 = new Game(
                1,
                "The Witcher 3: Wild Hunt (Mock)",
                92,
                "2015",
                "https://media.rawg.io/media/games/618/618c2031a07bbff6b4f611f10b6bcdbc.jpg",
                List.of("RPG", "Action"),
                "https://store.steampowered.com/app/292030",
                292030
        );

        Game game2 = new Game(
                2,
                "Cyberpunk 2077 (Mock)",
                86,
                "2020",
                "https://media.rawg.io/media/games/26d/26d4437715bee60138dab4a7c8c59c6b.jpg",
                List.of("RPG", "Action", "FPS"),
                "https://store.steampowered.com/app/1091500",
                1091500
        );

        return Flux.fromIterable(List.of(game1, game2));
    }
}