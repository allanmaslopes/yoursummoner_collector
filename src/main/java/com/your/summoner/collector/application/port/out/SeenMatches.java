package com.your.summoner.collector.application.port.out;

public interface SeenMatches {
    /** True na primeira vez que o matchId aparece (memória de 30 dias). */
    boolean markIfNew(String matchId);

    /** Desfaz a marca de uma partida que não chegou a ser tratada (pausa ou falha no meio do caminho). */
    void forget(String matchId);
}
