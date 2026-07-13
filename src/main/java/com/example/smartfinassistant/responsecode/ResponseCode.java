package com.example.smartfinassistant.responsecode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reference / ground-truth row for a payment response code (RC).
 * Seeded by Flyway (V2); authoritative for RC meanings.
 */
@Entity
@Table(name = "response_codes")
@Getter
@Setter
@NoArgsConstructor
public class ResponseCode {

    @Id
    @Column(name = "rc", length = 2)
    private String rc;

    @Column(name = "meaning", nullable = false)
    private String meaning;

    @Column(name = "handling", nullable = false)
    private String handling;
}
