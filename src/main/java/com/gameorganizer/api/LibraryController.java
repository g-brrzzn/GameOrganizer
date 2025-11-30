package com.gameorganizer.api;

import com.gameorganizer.domain.model.UserGame;
import com.gameorganizer.domain.repository.UserGameRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        System.out.println("Recebido pedido para deletar ID: " + id);
        return repository.deleteById(id);
    }
}