package com.gameorganizer.controller;

import com.gameorganizer.service.GameOrganizerService;
import com.gameorganizer.service.model.GameOverview;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GameController {
    private final GameOrganizerService service;

    public GameController(GameOrganizerService service) {
        this.service = service;
    }

    // Endpoint não-bloqueante: delega execução bloqueante para um scheduler apropriado
    @GetMapping("/api/games/organize")
    public Mono<List<GameOverview>> organize(@RequestParam("name") String name) {
        // Service atual usa block() internamente — rodamos isso em boundedElastic para não bloquear Netty
        return Mono.fromCallable(() -> service.organizeByName(name))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
