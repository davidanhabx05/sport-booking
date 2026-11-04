package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.sportbooking.dto.Requests.CancelBookingRequest;
import vn.sportbooking.dto.Requests.CreateBookingRequest;
import vn.sportbooking.dto.Requests.RecurringBookingRequest;
import vn.sportbooking.dto.Responses.BookingDto;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.service.BookingService;

import java.util.List;

@Tag(name = "Bookings", description = "Đặt sân, xem và huỷ đơn của tôi")
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@Valid @RequestBody CreateBookingRequest req) {
        return bookingService.create(CurrentUser.id(), req);
    }

    /** Đặt định kỳ hàng tuần (2–8 tuần), thanh toán một lần cho cả nhóm. */
    @PostMapping("/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BookingDto> createRecurring(@Valid @RequestBody RecurringBookingRequest req) {
        return bookingService.createRecurring(CurrentUser.id(), req);
    }

    @GetMapping("/my")
    public List<BookingDto> my() {
        return bookingService.myBookings(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public BookingDto get(@PathVariable Long id) {
        return bookingService.get(CurrentUser.require(), id);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelBookingRequest req) {
        return bookingService.cancelByCustomer(CurrentUser.id(), id, req == null ? null : req.reason());
    }
}
