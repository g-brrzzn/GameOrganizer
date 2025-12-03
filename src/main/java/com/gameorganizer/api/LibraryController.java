package com.gameorganizer.api;

import com.gameorganizer.domain.model.UserGame;
import com.gameorganizer.domain.repository.UserGameRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    private final UserGameRepository repository;

    public LibraryController(UserGameRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Flux<UserGame> getLibrary() {
        return repository.findAll();
    }

    @PostMapping
    public Mono<UserGame> addToLibrary(@RequestBody UserGame game) {
        return repository.findByRawgId(game.getRawgId())
                .flatMap(existingGame -> {
                    existingGame.setGameStatus(game.getGameStatus());
                    existingGame.setRating(game.getRating());
                    return repository.save(existingGame);
                })
                .switchIfEmpty(repository.save(game));
    }

    @DeleteMapping("/{id}")
    public Mono<Void> removeFromLibrary(@PathVariable("id") Long id) {
        return repository.deleteById(id);
    }
    @PostMapping("/reorder")
    public Mono<Void> reorderLibrary(@RequestBody List<Long> orderedIds) {
        return Flux.fromIterable(orderedIds)
                .index()
                .flatMap(tuple -> {
                    Long index = tuple.getT1();
                    Long id = tuple.getT2();
                    return repository.findById(id)
                            .flatMap(game -> {
                                game.setListOrder(index.intValue());
                                return repository.save(game);
                            });
                })
                .then();
    }
}