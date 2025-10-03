package com.gameorganizer.controller;

import com.gameorganizer.service.GameOrganizerService;
import com.gameorganizer.service.model.GameOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@RestController
public class GameController {

    private final GameOrganizerService service;

    public GameController(GameOrganizerService service) {
        this.service = service;
    }

    /**
     * Endpoint não-bloqueante para buscar e organizar dados de jogos.
     * O pipeline reativo agora flui do serviço até o controller sem nenhum bloqueio.
     *
     * @param name O nome do jogo para pesquisar.
     * @return Um Mono contendo a lista final de jogos enriquecidos, ordenados por playtime.
     */
    @GetMapping("/api/games/organize")
    public Mono<List<GameOverview>> organize(@RequestParam("name") String name) {
        // 1. Chama diretamente o método reativo do serviço, que retorna um Flux<GameOverview>.
        return service.organizeByNameReactive(name)
                // 2. O operador .sort() do Flux aguarda todos os elementos, os ordena em memória
                //    e então os reemite na ordem correta.
                .sort(Comparator.comparingInt((GameOverview g) -> g.getPlaytime() == null ? 0 : g.getPlaytime()).reversed())
                // 3. O operador .collectList() agrupa todos os elementos do Flux ordenado em uma List
                //    e retorna um Mono<List<GameOverview>>, que é o que será serializado como JSON.
                .collectList();
    }
}