package vn.sportbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportbooking.entity.Review;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("select r from Review r join fetch r.user where r.venue.id = :venueId order by r.createdAt desc")
    List<Review> findByVenue(@Param("venueId") Long venueId);

    @Query("select avg(r.rating) from Review r where r.venue.id = :venueId")
    Double averageRating(@Param("venueId") Long venueId);

    long countByVenueId(Long venueId);

    boolean existsByVenueIdAndUserId(Long venueId, Long userId);
}
