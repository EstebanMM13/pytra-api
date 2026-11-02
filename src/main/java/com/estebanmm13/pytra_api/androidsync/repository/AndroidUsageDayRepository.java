package com.estebanmm13.pytra_api.androidsync.repository;

import com.estebanmm13.pytra_api.androidsync.model.AndroidUsageDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AndroidUsageDayRepository extends JpaRepository<AndroidUsageDay, AndroidUsageDay.Key> {

    /** Creates the day's baseline at 0 minutes unless it exists; race-free (concurrent syncs never collide). */
    @Modifying
    @Query(value = """
            INSERT INTO android_usage_days (user_id, package_name, usage_date, minutes_recorded)
            VALUES (:userId, :packageName, :usageDate, 0)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("userId") Long userId, @Param("packageName") String packageName,
                      @Param("usageDate") LocalDate usageDate);

    /** The day's baseline, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT d FROM AndroidUsageDay d
            WHERE d.userId = :userId AND d.packageName = :packageName AND d.usageDate = :usageDate
            """)
    Optional<AndroidUsageDay> findForUpdate(@Param("userId") Long userId, @Param("packageName") String packageName,
                                            @Param("usageDate") LocalDate usageDate);

    /** Every recorded day of one package of the user. */
    List<AndroidUsageDay> findAllByUserIdAndPackageName(Long userId, String packageName);

    @Modifying
    @Query("DELETE FROM AndroidUsageDay d WHERE d.userId = :userId AND d.packageName = :packageName")
    void deleteAllOf(@Param("userId") Long userId, @Param("packageName") String packageName);
}
