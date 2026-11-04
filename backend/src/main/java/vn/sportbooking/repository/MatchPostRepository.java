package vn.sportbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.MatchPost;

import java.time.LocalDate;
import java.util.List;

public interface MatchPostRepository extends JpaRepository<MatchPost, Long> {

    @Query("""
            select m from MatchPost m
            join fetch m.booking b join fetch b.court c join fetch c.venue v join fetch m.host
            where b.bookingDate >= :today
              and m.status <> :closed
              and b.status = :confirmed
              and (:sport is null or c.sportType = :sport)
              and (:level is null or m.skillLevel = :level)
              and (:district is null or v.district = :district)
            order by b.bookingDate asc, b.startTime asc
            """)
    List<MatchPost> findUpcoming(@Param("today") LocalDate today,
                                 @Param("sport") Enums.SportType sport,
                                 @Param("level") Enums.SkillLevel level,
                                 @Param("district") String district,
                                 @Param("closed") Enums.MatchStatus closed,
                                 @Param("confirmed") Enums.BookingStatus confirmed);

    default List<MatchPost> findUpcoming(LocalDate today, Enums.SportType sport, Enums.SkillLevel level, String district) {
        return findUpcoming(today, sport, level, district, Enums.MatchStatus.CLOSED, Enums.BookingStatus.CONFIRMED);
    }

    boolean existsByBookingId(Long bookingId);

    java.util.Optional<MatchPost> findByBookingId(Long bookingId);

    @Query("""
            select distinct m from MatchPost m left join m.participants p
            where m.host.id = :userId or p.user.id = :userId
            order by m.createdAt desc
            """)
    List<MatchPost> findMine(@Param("userId") Long userId);
}
