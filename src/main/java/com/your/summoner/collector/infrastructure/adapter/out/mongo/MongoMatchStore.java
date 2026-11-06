package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import com.your.summoner.collector.application.port.out.MatchStore;
import com.your.summoner.collector.domain.model.CollectedMatch;
import com.your.summoner.collector.domain.model.PatchVersion;
import com.your.summoner.collector.domain.model.Region;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class MongoMatchStore implements MatchStore {
    private final MongoTemplate mongo;

    MongoMatchStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean saveIfAbsent(CollectedMatch m) {
        try {
            mongo.insert(MatchDocument.from(m));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public long deleteOlderThan(PatchVersion oldestKept) {
        Query q = Query.query(new Criteria().orOperator(
                Criteria.where("patchMajor").lt(oldestKept.major()),
                Criteria.where("patchMajor").is(oldestKept.major()).and("patchMinor").lt(oldestKept.minor())));
        return mongo.remove(q, MatchDocument.class).getDeletedCount();
    }

    @Override
    public long countSince(Instant since, Region r) {
        return mongo.count(Query.query(Criteria.where("collectedAt").gte(since).and("region").is(r.name())),
                MatchDocument.class);
    }
}
