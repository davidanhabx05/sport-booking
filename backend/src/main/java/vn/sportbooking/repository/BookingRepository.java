package vn.sportbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportbooking.entity.Booking;
import vn.sportbooking.entity.Enums;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Đếm đơn đang "chiếm" sân và chồng lấn khoảng [start, end).
     * Hai khoảng giao nhau khi: start < existing.end AND end > existing.start.
     * Đơn chờ thanh toán chỉ chiếm sân khi chưa hết hạn giữ chỗ.
     */
    @Query("""
            select count(b) from Booking b
            where b.court.id = :courtId
              and b.bookingDate = :date
              and b.startTime < :endTime
              and b.endTime > :startTime
              and (b.status in :paid or (b.status = :pending and b.holdExpiresAt > :now))
              and (:excludeId is null or b.id <> :excludeId)
            """)
    long countOverlapping(@Param("courtId") Long courtId,
                          @Param("date") LocalDate date,
                          @Param("startTime") LocalTime startTime,
                          @Param("endTime") LocalTime endTime,
                          @Param("now") LocalDateTime now,
                          @Param("excludeId") Long excludeId,
                          @Param("paid") Collection<Enums.BookingStatus> paid,
                          @Param("pending") Enums.BookingStatus pending);

    /** Trạng thái được coi là đã chiếm sân chắc chắn. */
    List<Enums.BookingStatus> PAID = List.of(Enums.BookingStatus.CONFIRMED, Enums.BookingStatus.COMPLETED);

    default long countOverlapping(Long courtId, LocalDate date, LocalTime startTime, LocalTime endTime,
                                  LocalDateTime now, Long excludeId) {
        return countOverlapping(courtId, date, startTime, endTime, now, excludeId, PAID, Enums.BookingStatus.PENDING_PAYMENT);
    }

    /** Các đơn đang chiếm sân của một cụm sân trong một ngày (để vẽ lưới giờ trống). */
    @Query("""
            select b from Booking b
            where b.court.venue.id = :venueId
              and b.bookingDate = :date
              and (b.status in :paid or (b.status = :pending and b.holdExpiresAt > :now))
            """)
    List<Booking> findActiveByVenueAndDate(@Param("venueId") Long venueId,
                                           @Param("date") LocalDate date,
                                           @Param("now") LocalDateTime now,
                                           @Param("paid") Collection<Enums.BookingStatus> paid,
                                           @Param("pending") Enums.BookingStatus pending);

    default List<Booking> findActiveByVenueAndDate(Long venueId, LocalDate date, LocalDateTime now) {
        return findActiveByVenueAndDate(venueId, date, now, PAID, Enums.BookingStatus.PENDING_PAYMENT);
    }

    @Query("""
            select b from Booking b join fetch b.court c join fetch c.venue
            where b.user.id = :userId
            order by b.bookingDate desc, b.startTime desc
            """)
    List<Booking> findByUserWithCourt(@Param("userId") Long userId);

    Optional<Booking> findByCode(String code);

    List<Booking> findByGroupCodeOrderByBookingDateAsc(String groupCode);

    List<Booking> findByStatusAndHoldExpiresAtBefore(Enums.BookingStatus status, LocalDateTime time);

    List<Booking> findByStatusAndBookingDateBefore(Enums.BookingStatus status, LocalDate date);

    /** Truy vấn cho trang quản trị: lọc theo danh sách cụm sân, khoảng ngày và trạng thái. */
    @Query("""
            select b from Booking b join fetch b.court c join fetch c.venue v join fetch b.user u
            where v.id in :venueIds
              and b.bookingDate between :from and :to
              and (:status is null or b.status = :status)
            order by b.bookingDate desc, b.startTime asc
            """)
    List<Booking> findForAdmin(@Param("venueIds") Collection<Long> venueIds,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("status") Enums.BookingStatus status);

    boolean existsByUserIdAndCourtVenueIdAndStatusIn(Long userId, Long venueId, Collection<Enums.BookingStatus> statuses);
}
