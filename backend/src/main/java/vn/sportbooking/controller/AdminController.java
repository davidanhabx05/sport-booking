package vn.sportbooking.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.sportbooking.dto.Requests.*;
import vn.sportbooking.dto.Responses.*;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.security.CurrentUser;
import vn.sportbooking.service.*;

import java.time.LocalDate;
import java.util.List;

/** Trang quản trị cho Chủ sân (OWNER – chỉ thấy sân của mình) và Quản trị viên (ADMIN – thấy tất cả). */
@Tag(name = "Admin", description = "Quản lý cụm sân, sân, đơn đặt, thống kê, người dùng")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final VenueService venueService;
    private final BookingService bookingService;
    private final StatsService statsService;
    private final UserAdminService userAdminService;

    // ---------- Cụm sân & sân ----------

    @GetMapping("/venues")
    public List<VenueDetailDto> venues() {
        return venueService.managedVenues(CurrentUser.require());
    }

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    public VenueDetailDto createVenue(@Valid @RequestBody VenueRequest req) {
        return venueService.createVenue(CurrentUser.require(), req);
    }

    @PutMapping("/venues/{id}")
    public VenueDetailDto updateVenue(@PathVariable Long id, @Valid @RequestBody VenueRequest req) {
        return venueService.updateVenue(CurrentUser.require(), id, req);
    }

    /** Tải ảnh đại diện cho cụm sân (JPG/PNG/WEBP, tối đa 5MB). */
    @PostMapping(path = "/venues/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VenueDetailDto uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return venueService.updateImage(CurrentUser.require(), id, file);
    }

    @PostMapping("/venues/{venueId}/courts")
    @ResponseStatus(HttpStatus.CREATED)
    public CourtDto addCourt(@PathVariable Long venueId, @Valid @RequestBody CourtRequest req) {
        return venueService.addCourt(CurrentUser.require(), venueId, req);
    }

    @PutMapping("/courts/{courtId}")
    public CourtDto updateCourt(@PathVariable Long courtId, @Valid @RequestBody CourtRequest req) {
        return venueService.updateCourt(CurrentUser.require(), courtId, req);
    }

    // ---------- Đơn đặt sân ----------

    @GetMapping("/bookings")
    public List<BookingDto> bookings(@RequestParam(required = false) Long venueId,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) Enums.BookingStatus status) {
        return bookingService.adminList(CurrentUser.require(), venueId, from, to, status);
    }

    @PostMapping("/bookings/{id}/check-in")
    public BookingDto checkIn(@PathVariable Long id) {
        return bookingService.adminCheckIn(CurrentUser.require(), id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id, @RequestBody(required = false) CancelBookingRequest req) {
        return bookingService.adminCancel(CurrentUser.require(), id, req == null ? null : req.reason());
    }

    // ---------- Thống kê ----------

    @GetMapping("/stats")
    public StatsDto stats(@RequestParam(required = false) Long venueId,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.stats(CurrentUser.require(), venueId, from, to);
    }

    // ---------- Người dùng (chỉ ADMIN) ----------

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> users() {
        return userAdminService.list();
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return userAdminService.update(CurrentUser.id(), id, req);
    }
}
