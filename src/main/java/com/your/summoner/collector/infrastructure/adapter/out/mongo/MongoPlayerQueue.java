package com.your.summoner.collector.infrastructure.adapter.out.mongo;

import com.your.summoner.collector.application.port.out.PlayerQueue;
import com.your.summoner.collector.application.port.out.QueuedPlayer;
import com.your.summoner.collector.domain.model.Platform;
import com.your.summoner.collector.domain.model.Region;
import com.your.summoner.collector.domain.model.Tier;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
class MongoPlayerQueue implements PlayerQueue {
    static final String COLLECTION = "player_queue";
    private final MongoTemplate mongo;

    MongoPlayerQueue(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void offer(Platform p, Tier tier, List<String> puuids) {
        for (String puuid : puuids) {
            // Casa só se o existente tem tier menor; com tier igual/maior o upsert bate no índice único e nada muda.
            Query q = Query.query(Criteria.where("platform").is(p.name()).and("puuid").is(puuid)
                    .and("tierRank").lt(tier.ordinal()));
            Update u = new Update().set("tier", tier.name()).set("tierRank", tier.ordinal())
                    .setOnInsert("region", p.matchRegion().name()).setOnInsert("enqueuedAt", new Date());
            try {
                mongo.upsert(q, u, COLLECTION);
            } catch (DuplicateKeyException e) {
                // já na fila com tier igual ou maior
            }
        }
    }

    @Override
    public Optional<QueuedPlayer> poll(Region r) {
        Query q = Query.query(Criteria.where("region").is(r.name()))
                .with(Sort.by(Sort.Order.desc("tierRank"), Sort.Order.asc("enqueuedAt")));
        Document d = mongo.findAndRemove(q, Document.class, COLLECTION);
        return Optional.ofNullable(d).map(x -> new QueuedPlayer(Platform.valueOf(x.getString("platform")),
                Tier.valueOf(x.getString("tier")), x.getString("puuid")));
    }

    @Override
    public int pending(Region r) {
        return (int) mongo.count(Query.query(Criteria.where("region").is(r.name())), COLLECTION);
    }
}
