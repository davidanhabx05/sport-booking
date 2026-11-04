package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.ReviewRequest;
import vn.sportbooking.dto.Responses.ReviewDto;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.Review;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final VenueRepository venueRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<ReviewDto> list(Long venueId) {
        return reviewRepository.findByVenue(venueId).stream().map(ReviewDto::of).toList();
    }

    /** Chỉ khách đã từng đặt sân thành công tại cụm sân mới được đánh giá (mỗi người 1 lần). */
    @Transactional
    public ReviewDto create(Long userId, Long venueId, ReviewRequest req) {
        var venue = venueRepository.findById(venueId).orElseThrow(() -> ApiException.notFound("Không tìm thấy cụm sân"));
        boolean played = bookingRepository.existsByUserIdAndCourtVenueIdAndStatusIn(userId, venueId,
                List.of(Enums.BookingStatus.CONFIRMED, Enums.BookingStatus.COMPLETED));
        if (!played) throw ApiException.badRequest("Bạn cần đặt sân thành công tại đây trước khi đánh giá");
        if (reviewRepository.existsByVenueIdAndUserId(venueId, userId)) {
            throw ApiException.conflict("Bạn đã đánh giá cụm sân này");
        }
        Review r = Review.builder()
                .venue(venue).user(userRepository.getReferenceById(userId))
                .rating(req.rating()).comment(req.comment()).build();
        reviewRepository.save(r);
        return ReviewDto.of(reviewRepository.findById(r.getId()).orElseThrow());
    }
}
