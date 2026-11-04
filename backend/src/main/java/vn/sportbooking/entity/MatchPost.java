package vn.sportbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bài "ghép kèo": người đã đặt sân đăng tin tìm thêm người chơi cùng.
 */
@Entity
@Table(name = "match_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id")
    private User host;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.SkillLevel skillLevel;

    /** Số người còn cần tìm thêm. */
    @Column(nullable = false)
    private Integer slotsNeeded;

    /** Chi phí mỗi người góp (VND). */
    private Long pricePerPerson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Enums.MatchStatus status;

    @Builder.Default
    @OneToMany(mappedBy = "matchPost", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchParticipant> participants = new ArrayList<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public long acceptedCount() {
        return participants.stream().filter(p -> p.getStatus() == Enums.ParticipantStatus.ACCEPTED).count();
    }
}
