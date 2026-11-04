package vn.sportbooking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportbooking.entity.Court;

import java.util.Optional;

public interface CourtRepository extends JpaRepository<Court, Long> {

    /**
     * Khoá bi quan (SELECT ... FOR UPDATE) trên dòng sân.
     * Mọi giao dịch đặt cùng một sân sẽ phải xếp hàng tại đây,
     * nhờ vậy bước "kiểm tra trùng giờ rồi mới lưu" không bị chen ngang → chống double-booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c join fetch c.venue where c.id = :id")
    Optional<Court> findByIdForUpdate(@Param("id") Long id);
}
