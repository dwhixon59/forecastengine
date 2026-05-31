package com.hixon.financialApp.model.register;

import com.hixon.financialApp.utility.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Enhancement 2: Persists resolved payee→register mappings so that future import sessions
 * can auto-resolve the same transfer without prompting the user.
 *
 * <p>The {@code transfer_memo_mapping} table stores one row per (payee_pattern, id_register)
 * pair. Each time the same pattern is resolved to the same register, {@code usage_count}
 * is incremented and {@code last_used} is updated.</p>
 *
 * <h4>Lookup algorithm:</h4>
 * <ol>
 *   <li>Normalize the payee string (upper-case, collapse whitespace, strip trailing
 *       REF#... codes).</li>
 *   <li>Query for an exact match on {@code payee_pattern}.</li>
 *   <li>Return the {@link Register} if the row's {@code usage_count ≥ 1}, otherwise
 *       treat it as low-confidence and let the caller decide.</li>
 * </ol>
 */
public class TransferMemoMapping {

    private static final Logger logger = LoggerFactory.getLogger(TransferMemoMapping.class);

    // ── Schema ──────────────────────────────────────────────────────────────
    // CREATE TABLE transfer_memo_mapping (
    //   id            INT AUTO_INCREMENT PRIMARY KEY,
    //   payee_pattern VARCHAR(512) NOT NULL,
    //   id_register   BINARY(16)  NOT NULL,
    //   usage_count   INT         NOT NULL DEFAULT 1,
    //   last_used     DATE        NOT NULL,
    //   UNIQUE KEY uq_pattern_register (payee_pattern, id_register)
    // ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    // ── Fields ───────────────────────────────────────────────────────────────
    private final String payeePattern;
    private final UUID   idRegister;

    // ── Constructor ──────────────────────────────────────────────────────────
    private TransferMemoMapping(String payeePattern, UUID idRegister) {
        this.payeePattern = payeePattern;
        this.idRegister   = idRegister;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Look up the Register previously resolved for {@code rawPayee}.
     * Returns {@code null} if no mapping is found or if the table does not yet exist.
     *
     * @param rawPayee the full payee string as supplied to {@code resolveUnmatchedAccount()}
     * @return the previously resolved {@link Register}, or {@code null}
     */
    public static Register findRegisterForPayee(String rawPayee) {
        if (rawPayee == null || rawPayee.isBlank()) return null;

        String pattern = normalize(rawPayee);
        String sql = "SELECT bin_to_uuid(id_register) AS id_register, usage_count " +
                     "FROM transfer_memo_mapping " +
                     "WHERE payee_pattern = '" + escapeSql(pattern) + "' " +
                     "ORDER BY usage_count DESC LIMIT 1";
        try (Statement stmt = Utility.getDbConnection().createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {

            if (rs.next()) {
                UUID registerId = UUID.fromString(rs.getString("id_register"));
                int  usageCount = rs.getInt("usage_count");
                logger.debug("[TransferMemoMapping] HIT  payee='{}' → register={} (used {} times)",
                        pattern, registerId, usageCount);
                return Register.getById(registerId);
            }
        } catch (Exception e) {
            // Table may not exist yet, or any other DB problem — degrade gracefully
            logger.debug("[TransferMemoMapping] lookup failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * Persist (or update) a payee→register mapping after a successful manual resolution.
     * Uses INSERT … ON DUPLICATE KEY UPDATE so it is idempotent.
     *
     * @param rawPayee  the full payee string
     * @param register  the register the user selected
     */
    public static void save(String rawPayee, Register register) {
        if (rawPayee == null || rawPayee.isBlank() || register == null) return;

        String pattern    = normalize(rawPayee);
        String registerId = register.getId().toString();
        String sql = "INSERT INTO transfer_memo_mapping (payee_pattern, id_register, usage_count, last_used) " +
                     "VALUES ('" + escapeSql(pattern) + "', uuid_to_bin('" + registerId + "'), 1, CURDATE()) " +
                     "ON DUPLICATE KEY UPDATE usage_count = usage_count + 1, last_used = CURDATE()";
        try (Statement stmt = Utility.getDbConnection().createStatement()) {
            stmt.executeUpdate(sql);
            logger.debug("[TransferMemoMapping] SAVE payee='{}' → register='{}'", pattern, register.getName());
        } catch (Exception e) {
            // Degrade gracefully if the table doesn't exist yet
            logger.warn("[TransferMemoMapping] save failed: {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Normalize a payee string for consistent matching:
     * <ul>
     *   <li>Upper-case</li>
     *   <li>Collapse internal whitespace to single spaces</li>
     *   <li>Strip trailing REF# codes (e.g., "REF #IB0Y2MCFXJ ON 05/11/26")</li>
     *   <li>Trim</li>
     * </ul>
     */
    static String normalize(String payee) {
        if (payee == null) return "";
        // Remove REF# codes and everything after them
        String s = payee.replaceAll("(?i)\\bREF\\s*#\\S+.*", "").trim();
        // Collapse whitespace
        s = s.replaceAll("\\s+", " ").toUpperCase().trim();
        return s;
    }

    /** Minimal SQL-injection protection for string literals. */
    private static String escapeSql(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}

