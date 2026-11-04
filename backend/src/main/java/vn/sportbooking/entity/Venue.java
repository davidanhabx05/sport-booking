package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Cụm sân (ví dụ: "Sân Pickleball Cầu Giấy") – gồm nhiều sân con (Court). */
@Entity
@Table(name = "venues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 60)
    private String district;

    @Column(length = 1000)
    private String description;

    @Column(length = 20)
    private String phone;

    private String imageUrl;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    /** Khung giờ cao điểm áp dụng giá peak. */
    private LocalTime peakStart;
    private LocalTime peakEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<Court> courts = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isPeak(LocalTime t) {
        if (peakStart == null || peakEnd == null) return false;
        return !t.isBefore(peakStart) && t.isBefore(peakEnd);
    }
}
