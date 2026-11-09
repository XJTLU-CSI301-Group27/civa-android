package edu.xjtlu.civa.server.application;

import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Candidate shared transaction boundary. Callers own canonical digest and authorization. */
public final class IdempotencyExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public IdempotencyExecutor(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    public Result execute(UUID actor, UUID key, byte[] digest, Supplier<Result> work, Consumer<Result> authorizeReplay) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(key);
        Objects.requireNonNull(work);
        Objects.requireNonNull(authorizeReplay);
        if (digest == null || digest.length != 32) throw new IllegalArgumentException("Expected SHA-256 digest");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Idempotency must start the shared transaction");
        byte[] expectedDigest = digest.clone();
        try {
            return transactions.execute(status -> {
                try {
                    jdbc.update("INSERT INTO idempotency_requests(actor_id,key,operation_digest) VALUES (?,?,?)", actor, key, expectedDigest);
                } catch (DuplicateKeyException ex) {
                    throw new ExistingKey();
                }
                Result result = Objects.requireNonNull(work.get());
                int updated = jdbc.update("UPDATE idempotency_requests SET http_status=?,response_body=CAST(? AS jsonb) WHERE actor_id=? AND key=?",
                        result.status, result.body, actor, key);
                if (updated != 1) throw new IllegalStateException("Idempotency placeholder missing");
                return result;
            });
        } catch (ExistingKey ex) {
            return transactions.execute(status -> {
                List<Stored> rows = jdbc.query("SELECT operation_digest,http_status,response_body::text FROM idempotency_requests WHERE actor_id=? AND key=?",
                        (rs, n) -> new Stored(rs.getBytes(1), new Result(rs.getInt(2), rs.getString(3))), actor, key);
                if (rows.size() != 1) throw new IllegalStateException("Committed idempotency result missing");
                Stored stored = rows.get(0);
                if (!MessageDigest.isEqual(expectedDigest, stored.digest)) throw new KeyReused();
                authorizeReplay.accept(stored.result);
                return stored.result;
            });
        }
    }

    public static final class Result {
        public final int status;
        @Nullable public final String body;
        public Result(int status, @Nullable String body) {
            if (status < 200 || status > 299 || (status == 204 ? body != null : body == null)) {
                throw new IllegalArgumentException("Invalid successful response");
            }
            this.status = status;
            this.body = body;
        }
    }
    public static final class KeyReused extends RuntimeException {
        public KeyReused() { super("IDEMPOTENCY_KEY_REUSED"); }
    }
    private static final class ExistingKey extends RuntimeException { }
    private static final class Stored {
        final byte[] digest;
        final Result result;
        Stored(byte[] digest, Result result) { this.digest = digest; this.result = result; }
    }
}
