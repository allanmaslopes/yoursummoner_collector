package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Cria os índices na subida; idempotente. */
@Component
class CollectorIndexes implements InitializingBean {
    private final MongoTemplate mongo;

    CollectorIndexes(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void afterPropertiesSet() {
        var matches = mongo.indexOps("matches");
        matches.createIndex(new Index().on("matchId", Sort.Direction.ASC).unique());
        matches.createIndex(new Index().on("collectedAt", Sort.Direction.ASC));
        matches.createIndex(new Index().on("patch", Sort.Direction.ASC));
        mongo.indexOps(MongoSeenMatches.COLLECTION)
                .createIndex(new Index().on("seenAt", Sort.Direction.ASC).expire(Duration.ofDays(30)));
        var queue = mongo.indexOps(MongoPlayerQueue.COLLECTION);
        queue.createIndex(new Index().on("platform", Sort.Direction.ASC).on("puuid", Sort.Direction.ASC).unique());
        queue.createIndex(new Index().on("enqueuedAt", Sort.Direction.ASC).expire(Duration.ofHours(24)));
        queue.createIndex(new Index().on("region", Sort.Direction.ASC).on("tierRank", Sort.Direction.DESC)
                .on("enqueuedAt", Sort.Direction.ASC));
    }
}
