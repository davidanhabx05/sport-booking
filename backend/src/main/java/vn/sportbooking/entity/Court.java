package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

/** Một sân cụ thể trong cụm sân (ví dụ "Sân số 1"). */
@Entity
@Table(name = "courts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.SportType sportType;

    /** Giá giờ thường (VND/giờ). */
    @Column(nullable = false)
    private Long pricePerHour;

    /** Giá giờ cao điểm (VND/giờ). Null = dùng giá thường. */
    private Long peakPricePerHour;

    @Builder.Default
    private boolean indoor = true;

    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;
}
