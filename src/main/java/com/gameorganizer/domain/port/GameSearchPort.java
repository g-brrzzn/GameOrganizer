package com.gameorganizer.domain.port;
import com.gameorganizer.domain.model.Game;
import reactor.core.publisher.Flux;

public interface GameSearchPort {
    Flux<Game> searchGamesByName(String name);
}