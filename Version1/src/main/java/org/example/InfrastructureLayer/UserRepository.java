package org.example.InfrastructureLayer;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link IUserRepository}.
 *
 * <h2>Phase 1 — Thread-Safety Refactoring</h2>
 *
 * <p>The previous implementation stored users in a plain {@link java.util.HashMap}.
 * {@code HashMap} is not thread-safe: concurrent reads (e.g. {@code existsByEmail}
 * iterating the entry set) and concurrent writes ({@code add} calling
 * {@code put}) can cause a {@link java.util.ConcurrentModificationException} or,
 * worse, silently corrupt the internal table structure. This is not a
 * theoretical concern — it is reproducible under the load of
 * {@code UserServiceConcurrencyTest}.
 *
 * <h2>Two-layer fix</h2>
 *
 * <h3>Layer 1 — {@code ConcurrentHashMap}</h3>
 * <p>Both the {@code users} and {@code admins} maps are now
 * {@link ConcurrentHashMap}. This eliminates CME and makes individual
 * operations ({@code get}, {@code put}, iteration) thread-safe without a
 * global lock, so concurrent reads never block each other.
 *
 * <h3>Layer 2 — {@code registrationLock} inside {@code add}</h3>
 * <p>A {@code ConcurrentHashMap} makes each individual operation atomic,
 * but the three-step sequence "check email → check username → insert"
 * is <em>not</em> atomic as a whole. Two threads can both execute the
 * two existence checks, both see {@code false}, and both proceed to insert,
 * creating duplicate emails or usernames.
 *
 * <p>To close that race, {@link #add(User)} acquires an internal
 * {@code registrationLock} and re-validates both constraints inside it
 * before calling {@code put}. The service-layer pre-checks remain as a
 * fast-path optimisation (they avoid hashing and object allocation when
 * the conflict is already obvious), but {@code add} is the definitive
 * gate.
 *
 * <p>The lock is only acquired for <em>member</em> users (non-null email).
 * Guest users are created with a freshly-generated UUID — their uniqueness
 * is guaranteed by construction — so they bypass the lock entirely.
 *
 * <h2>SOLID alignment</h2>
 * <ul>
 *   <li><b>SRP</b> — uniqueness enforcement lives here, alongside the store
 *       that knows whether a value already exists; it is not scattered across
 *       multiple service layers.</li>
 *   <li><b>OCP</b> — the {@link IUserRepository} interface contract is
 *       unchanged; no caller is affected by this internal restructuring.</li>
 *   <li><b>DIP</b> — callers depend on {@code IUserRepository}, not on
 *       this concrete class.</li>
 * </ul>
 */
public class UserRepository implements IUserRepository {

    // ------------------------------------------------------------------
    // Primary store — thread-safe, no global lock for reads
    // ------------------------------------------------------------------

    /**
     * Main user store. {@code ConcurrentHashMap} guarantees safe concurrent
     * reads and writes at the individual-operation level.
     *
     * <p>Writes that must be atomic as a multi-step sequence (registration)
     * additionally acquire {@link #registrationLock}.
     */
    private final Map<UUID, User>  users  = new ConcurrentHashMap<>();

    /**
     * Admin store. Admins are added once at startup (via
     * {@link #addAdmin(Admin)}) and only read thereafter, so a
     * {@code ConcurrentHashMap} here is a straightforward, future-proof
     * choice.
     */
    private final Map<UUID, Admin> admins = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // Registration lock — closes the check-then-act race on add()
    // ------------------------------------------------------------------

    /**
     * Guards the compound "check email + check username + put" inside
     * {@link #add(User)}.
     *
     * <p>Two threads that reach {@code add()} with <em>different</em>
     * emails but the <em>same</em> username would both pass the
     * service-level pre-checks (which are serialised only per-email).
     * This lock serialises them at the repository level so that only one
     * of them can commit the insert. The lock is held only for the brief
     * duration of two map scans and one {@code put} — it is not a
     * bottleneck for reads or for adds with distinct keys.
     */
    private final Object registrationLock = new Object();

    // ------------------------------------------------------------------
    // IUserRepository — write operations
    // ------------------------------------------------------------------

    /**
     * Persists {@code user} in the store.
     *
     * <p><b>For member users (non-null email)</b>, this method acquires the
     * {@link #registrationLock} and atomically re-validates that neither the
     * email nor the username is already taken. If either constraint is
     * violated it throws {@link IllegalArgumentException} with a descriptive
     * message, allowing the caller to surface the conflict cleanly.
     *
     * <p><b>For guest users (null email)</b>, the UUID key guarantees
     * uniqueness by construction; the lock is skipped entirely.
     *
     * @param user the user to persist; must not be {@code null}
     * @throws IllegalArgumentException if a member user's email or username
     *                                  is already present in the store
     */
    @Override
    public void add(User user) {
        if (user.getEmail() == null) {
            // Guest: UUID is unique by construction; no uniqueness check needed.
            users.put(user.getId(), user);
            return;
        }

        // Member registration: the three-step check-and-insert must be atomic.
        synchronized (registrationLock) {
            if (existsByEmail(user.getEmail())) {
                throw new IllegalArgumentException("User email already exists.");
            }
            if (user.getUsername() != null && existsByUsername(user.getUsername())) {
                throw new IllegalArgumentException("Username already exists.");
            }
            users.put(user.getId(), user);
        }
    }

    // ------------------------------------------------------------------
    // IUserRepository — read operations
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Backed by {@code ConcurrentHashMap.get} — O(1), fully thread-safe,
     * no iteration.
     */
    @Override
    public Optional<User> getUser(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Backed by {@code ConcurrentHashMap.containsKey} — O(1), fully
     * thread-safe.
     */
    @Override
    public boolean exists(UUID userId) {
        return users.containsKey(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the {@code admins} map. {@code ConcurrentHashMap} iteration
     * is weakly consistent — it reflects the map state at or after the
     * start of the iteration. This is correct for admin lookups: admins are
     * added once at startup and never removed.
     */
    @Override
    public boolean isSystemAdmin(String username) {
        if (username == null) {
            return false;
        }

        return admins.values().stream().anyMatch(admin -> {
            if (username.equals(admin.getUsername())) {
                return true;
            }

            User user = users.get(admin.getId());

            if (user == null) {
                return false;
            }

            return username.equals(user.getUsername())
                    || username.equals(user.getEmail());
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the {@code users} map. {@code ConcurrentHashMap} iteration
     * is weakly consistent — safe under concurrent mutation; will never throw
     * {@link java.util.ConcurrentModificationException}.
     */
    @Override
    public boolean existsByEmail(String email) {
        if (email == null) return false;
        for (Map.Entry<UUID, User> entry : users.entrySet()) {
            if (email.equals(entry.getValue().getEmail())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the {@code users} map. Same thread-safety guarantees as
     * {@link #existsByEmail(String)}.
     */
    @Override
    public boolean existsByUsername(String username) {
        if (username == null) return false;
        for (Map.Entry<UUID, User> entry : users.entrySet()) {
            if (username.equals(entry.getValue().getUsername())) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the {@code users} map. Thread-safe via
     * {@code ConcurrentHashMap}'s weakly-consistent iterator.
     */
    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        for (Map.Entry<UUID, User> entry : users.entrySet()) {
            if (email.equals(entry.getValue().getEmail())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Backed by {@code ConcurrentHashMap.containsKey} — O(1).
     */
    @Override
    public boolean existsAdmin(UUID adminId) {
        return admins.containsKey(adminId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UUID> getCompaniesIdsByMember(String username) {
        User user = findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getCompanyRoles().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCompanyOwner(String username, UUID companyId) {
        User user = findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.isOwnerInCompany(companyId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasPermission(String username, UUID companyId,
                                  CompanyPermission permission, UUID eventId) {
        User user = findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.hasPremisions(companyId, permission, eventId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns an <em>unmodifiable view</em> of the internal map so that
     * callers can iterate safely without being able to bypass the uniqueness
     * constraints enforced by {@link #add(User)}.
     */
    @Override
    public Map<UUID, User> getAllUsers() {
        return Collections.unmodifiableMap(users);
    }

    // ------------------------------------------------------------------
    // Admin management (package-visible for DevDataSeeder / tests)
    // ------------------------------------------------------------------

    /**
     * Registers a system administrator. Called once during application
     * startup (via {@code DevDataSeeder}) and not part of the public
     * {@link IUserRepository} contract.
     *
     * @param admin the admin to register; must not be {@code null}
     */
    public void addAdmin(Admin admin) {
        admins.put(admin.getId(), admin);
    }
}
