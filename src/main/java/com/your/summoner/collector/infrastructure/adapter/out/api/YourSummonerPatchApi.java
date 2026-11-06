package com.your.summoner.collector.infrastructure.adapter.out.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "yoursummoner-patches", url = "${collector.yoursummoner-api-url:http://localhost:8080/your-summoner}")
public interface YourSummonerPatchApi {

    /** Patches do jogo, do mais novo para o mais antigo. */
    @GetMapping("/patches")
    List<PatchDto> patches();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PatchDto(String version, boolean active) {}
}
