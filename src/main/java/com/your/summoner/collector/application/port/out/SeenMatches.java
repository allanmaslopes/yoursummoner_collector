package com.your.summoner.collector.application.port.out;

public interface SeenMatches {
    /** True na primeira vez que o matchId aparece (memória de 30 dias). */
    boolean markIfNew(String matchId);
}
