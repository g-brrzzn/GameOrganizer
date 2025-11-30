package com.gameorganizer.domain.repository;

import com.gameorganizer.domain.model.UserGame;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserGameRepository extends ReactiveCrudRepository<UserGame, Long> {
    Mono<UserGame> findByRawgId(Integer rawgId);
    Flux<UserGame> findByGameStatus(String gameStatus);
}