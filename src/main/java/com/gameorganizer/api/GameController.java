package com.gameorganizer.api;

import com.gameorganizer.domain.GameService;
import com.gameorganizer.api.dto.GameResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@RestController
public class GameController {

    private final GameService service;

    public GameController(GameService service) {
        this.service = service;
    }

    @GetMapping("/api/games/organize")
    public Mono<List<GameResponseDto>> organize(@RequestParam("name") String name) {
        return service.organizeByNameReactive(name)
                .sort(Comparator.comparingInt((GameResponseDto g) ->
                        g.getMetacritic() == null ? 0 : g.getMetacritic()).reversed())
                .collectList();
    }
}