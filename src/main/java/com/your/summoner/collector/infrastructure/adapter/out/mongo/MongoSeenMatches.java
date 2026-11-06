package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import com.your.summoner.collector.application.port.out.SeenMatches;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
class MongoSeenMatches implements SeenMatches {
    static final String COLLECTION = "seen_matches";
    private final MongoTemplate mongo;

    MongoSeenMatches(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean markIfNew(String matchId) {
        try {
            mongo.insert(new Document("_id", matchId).append("seenAt", new Date()), COLLECTION);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
