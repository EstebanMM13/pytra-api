package com.estebanmm13.pytra_api.androidsync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Minutes of one Android package already recorded for one local day: the baseline the phone's daily
 * totals are compared with, so re-sending the same total adds nothing.
 */
@Entity
@Table(name = "android_usage_days")
@IdClass(AndroidUsageDay.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AndroidUsageDay {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "package_name")
    private String packageName;

    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "minutes_recorded", nullable = false)
    private int minutesRecorded;

    public record Key(Long userId, String packageName, LocalDate usageDate) implements Serializable {
        public Key() {
            this(null, null, null);
        }
    }
}
