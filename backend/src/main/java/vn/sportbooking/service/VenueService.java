package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Requests.CourtRequest;
import vn.sportbooking.dto.Requests.VenueRequest;
import vn.sportbooking.dto.Responses.*;
import vn.sportbooking.entity.*;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.*;
import vn.sportbooking.security.UserPrincipal;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final FileStorageService fileStorageService;

    // ===================== PUBLIC =====================

    @Transactional(readOnly = true)
    public List<VenueSummaryDto> search(Enums.SportType sport, String district, String keyword) {
        return venueRepository.search(sport, blankToNull(district), blankToNull(keyword))
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public VenueDetailDto detail(Long id, boolean includeInactiveCourts) {
        Venue v = getVenue(id);
        if (!v.isActive() && !includeInactiveCourts) throw ApiException.notFound("Cụm sân không tồn tại");
        return toDetail(v, includeInactiveCourts);
    }

    /** Lưới giờ trống của tất cả sân trong cụm vào một ngày. */
    @Transactional(readOnly = true)
    public AvailabilityDto availability(Long venueId, LocalDate date, Enums.SportType sport) {
        Venue venue = getVenue(venueId);
        LocalDateTime now = LocalDateTime.now();
        List<Booking> active = bookingRepository.findActiveByVenueAndDate(venueId, date, now);

        Map<Long, List<Booking>> byCourt = new HashMap<>();
        for (Booking b : active) byCourt.computeIfAbsent(b.getCourt().getId(), k -> new ArrayList<>()).add(b);

        List<CourtAvailabilityDto> result = new ArrayList<>();
        for (Court court : venue.getCourts()) {
            if (!court.isActive() || (sport != null && court.getSportType() != sport)) continue;
            List<Booking> courtBookings = byCourt.getOrDefault(court.getId(), List.of());
            List<SlotDto> slots = new ArrayList<>();
            for (LocalTime t = venue.getOpenTime(); t.isBefore(venue.getCloseTime()); t = t.plusHours(1)) {
                LocalTime end = t.plusHours(1);
                final LocalTime s = t;
                String status;
                if (LocalDateTime.of(date, t).isBefore(now)) {
                    status = "PAST";
                } else if (courtBookings.stream().anyMatch(b -> b.getStartTime().isBefore(end) && b.getEndTime().isAfter(s))) {
                    status = "BOOKED";
                } else {
                    status = "AVAILABLE";
                }
                slots.add(new SlotDto(t, end, pricingService.priceForSlot(court, t), venue.isPeak(t), status));
            }
            result.add(new CourtAvailabilityDto(CourtDto.of(court), slots));
        }
        return new AvailabilityDto(venueId, date, result);
    }

    public MetaDto meta() {
        List<Map<String, String>> sports = Arrays.stream(Enums.SportType.values())
                .map(s -> Map.of("value", s.name(), "label", s.getLabel())).toList();
        List<Map<String, String>> levels = List.of(
                Map.of("value", "BEGINNER", "label", "Mới chơi"),
                Map.of("value", "INTERMEDIATE", "label", "Trung bình"),
                Map.of("value", "ADVANCED", "label", "Khá / Giỏi"));
        return new MetaDto(sports, venueRepository.findDistricts(), levels);
    }

    // ===================== OWNER / ADMIN =====================

    @Transactional(readOnly = true)
    public List<VenueDetailDto> managedVenues(UserPrincipal me) {
        List<Venue> venues = me.isAdmin() ? venueRepository.findAll() : venueRepository.findByOwnerIdOrderByNameAsc(me.getId());
        return venues.stream().map(v -> toDetail(v, true)).toList();
    }

    /** Danh sách id cụm sân mà người dùng được quản lý. */
    @Transactional(readOnly = true)
    public List<Long> managedVenueIds(UserPrincipal me) {
        List<Venue> venues = me.isAdmin() ? venueRepository.findAll() : venueRepository.findByOwnerIdOrderByNameAsc(me.getId());
        return venues.stream().map(Venue::getId).toList();
    }

    @Transactional
    public VenueDetailDto createVenue(UserPrincipal me, VenueRequest req) {
        validateHours(req);
        Venue v = new Venue();
        apply(v, req);
        v.setOwner(userRepository.getReferenceById(me.getId()));
        venueRepository.save(v);
        return toDetail(v, true);
    }

    @Transactional
    public VenueDetailDto updateVenue(UserPrincipal me, Long id, VenueRequest req) {
        validateHours(req);
        Venue v = getManagedVenue(me, id);
        apply(v, req);
        return toDetail(v, true);
    }

    @Transactional
    public VenueDetailDto updateImage(UserPrincipal me, Long venueId, org.springframework.web.multipart.MultipartFile file) {
        Venue v = getManagedVenue(me, venueId);
        v.setImageUrl(fileStorageService.storeImage(file, "venues"));
        return toDetail(v, true);
    }

    @Transactional
    public CourtDto addCourt(UserPrincipal me, Long venueId, CourtRequest req) {
        Venue v = getManagedVenue(me, venueId);
        Court c = new Court();
        c.setVenue(v);
        applyCourt(c, req);
        v.getCourts().add(c);
        courtRepository.save(c);
        return CourtDto.of(c);
    }

    @Transactional
    public CourtDto updateCourt(UserPrincipal me, Long courtId, CourtRequest req) {
        Court c = courtRepository.findById(courtId).orElseThrow(() -> ApiException.notFound("Không tìm thấy sân"));
        checkManage(me, c.getVenue());
        applyCourt(c, req);
        return CourtDto.of(c);
    }

    public Venue getManagedVenue(UserPrincipal me, Long venueId) {
        Venue v = getVenue(venueId);
        checkManage(me, v);
        return v;
    }

    public void checkManage(UserPrincipal me, Venue v) {
        if (!me.isAdmin() && (v.getOwner() == null || !v.getOwner().getId().equals(me.getId()))) {
            throw ApiException.forbidden("Bạn không quản lý cụm sân này");
        }
    }

    // ===================== HELPERS =====================

    private Venue getVenue(Long id) {
        return venueRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy cụm sân"));
    }

    private void validateHours(VenueRequest req) {
        if (req.openTime().getMinute() != 0 || req.closeTime().getMinute() != 0) {
            throw ApiException.badRequest("Giờ mở/đóng cửa phải là giờ chẵn (ví dụ 06:00, 22:00)");
        }
        if (!req.closeTime().isAfter(req.openTime())) {
            throw ApiException.badRequest("Giờ đóng cửa phải sau giờ mở cửa");
        }
        if ((req.peakStart() == null) != (req.peakEnd() == null)
                || (req.peakStart() != null && !req.peakEnd().isAfter(req.peakStart()))) {
            throw ApiException.badRequest("Khung giờ cao điểm không hợp lệ");
        }
    }

    private void apply(Venue v, VenueRequest req) {
        v.setName(req.name().trim());
        v.setAddress(req.address().trim());
        v.setDistrict(req.district().trim());
        v.setDescription(req.description());
        v.setPhone(req.phone());
        v.setImageUrl(blankToNull(req.imageUrl()));
        v.setOpenTime(req.openTime());
        v.setCloseTime(req.closeTime());
        v.setPeakStart(req.peakStart());
        v.setPeakEnd(req.peakEnd());
        if (req.active() != null) v.setActive(req.active());
    }

    private void applyCourt(Court c, CourtRequest req) {
        c.setName(req.name().trim());
        c.setSportType(req.sportType());
        c.setPricePerHour(req.pricePerHour());
        c.setPeakPricePerHour(req.peakPricePerHour());
        if (req.indoor() != null) c.setIndoor(req.indoor());
        if (req.active() != null) c.setActive(req.active());
    }

    private VenueSummaryDto toSummary(Venue v) {
        List<Court> courts = v.getCourts().stream().filter(Court::isActive).toList();
        List<Enums.SportType> sports = courts.stream().map(Court::getSportType).distinct().sorted().toList();
        Long minPrice = courts.stream().map(Court::getPricePerHour).min(Long::compare).orElse(null);
        return new VenueSummaryDto(v.getId(), v.getName(), v.getAddress(), v.getDistrict(), v.getImageUrl(),
                v.getOpenTime(), v.getCloseTime(), sports, minPrice,
                round1(reviewRepository.averageRating(v.getId())), reviewRepository.countByVenueId(v.getId()), v.isActive());
    }

    private VenueDetailDto toDetail(Venue v, boolean includeInactive) {
        List<CourtDto> courts = v.getCourts().stream()
                .filter(c -> includeInactive || c.isActive())
                .map(CourtDto::of).toList();
        return new VenueDetailDto(v.getId(), v.getName(), v.getAddress(), v.getDistrict(), v.getDescription(),
                v.getPhone(), v.getImageUrl(), v.getOpenTime(), v.getCloseTime(), v.getPeakStart(), v.getPeakEnd(),
                v.isActive(), courts, round1(reviewRepository.averageRating(v.getId())),
                reviewRepository.countByVenueId(v.getId()),
                v.getOwner() != null ? v.getOwner().getId() : null);
    }

    private static Double round1(Double d) {
        return d == null ? null : Math.round(d * 10) / 10.0;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
