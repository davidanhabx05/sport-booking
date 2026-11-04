package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportbooking.dto.Requests.ReviewRequest;
import vn.sportbooking.dto.Responses.*;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.service.VenueService;
import vn.sportbooking.service.payment.PaymentService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Venues", description = "Tìm cụm sân, xem chi tiết, lịch trống, đánh giá")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;
    private final ReviewService reviewService;
    private final PaymentService paymentService;

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        MetaDto m = venueService.meta();
        return Map.of("sports", m.sports(), "districts", m.districts(), "skillLevels", m.skillLevels(),
                "paymentMethods", paymentService.availableMethods());
    }

    @GetMapping("/venues")
    public List<VenueSummaryDto> search(@RequestParam(required = false) Enums.SportType sport,
                                        @RequestParam(required = false) String district,
                                        @RequestParam(required = false) String keyword) {
        return venueService.search(sport, district, keyword);
    }

    @GetMapping("/venues/{id}")
    public VenueDetailDto detail(@PathVariable Long id) {
        return venueService.detail(id, false);
    }

    @GetMapping("/venues/{id}/availability")
    public AvailabilityDto availability(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) Enums.SportType sport) {
        return venueService.availability(id, date, sport);
    }

    @GetMapping("/venues/{id}/reviews")
    public List<ReviewDto> reviews(@PathVariable Long id) {
        return reviewService.list(id);
    }

    @PostMapping("/venues/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto review(@PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return reviewService.create(CurrentUser.id(), id, req);
    }
}
