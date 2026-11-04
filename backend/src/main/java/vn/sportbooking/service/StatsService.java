package vn.sportbooking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sportbooking.dto.Responses.StatsDto;
import vn.sportbooking.entity.*;
import vn.sportbooking.entity.Enums.BookingStatus;
import vn.sportbooking.exception.ApiException;
import vn.sportbooking.repository.BookingRepository;
import vn.sportbooking.repository.VenueRepository;
import vn.sportbooking.security.UserPrincipal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Thống kê doanh thu và tỷ lệ lấp đầy cho chủ sân / quản trị viên. */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final VenueService venueService;

    @Transactional(readOnly = true)
    public StatsDto stats(UserPrincipal me, Long venueId, LocalDate from, LocalDate to) {
        LocalDate f = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate t = to != null ? to : LocalDate.now();
        if (t.isBefore(f)) throw ApiException.badRequest("Khoảng ngày không hợp lệ");

        List<Long> venueIds = venueId != null
                ? List.of(venueService.getManagedVenue(me, venueId).getId())
                : venueService.managedVenueIds(me);
        if (venueIds.isEmpty()) {
            return new StatsDto(f, t, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), List.of());
        }

        List<Booking> all = bookingRepository.findForAdmin(venueIds, f, t, null);
        List<Booking> paid = all.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
                .toList();

        long revenue = paid.stream().mapToLong(Booking::getTotalPrice).sum();
        long cancelled = all.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        // Doanh thu theo ngày (đủ mọi ngày trong khoảng, ngày không có đơn = 0)
        Map<LocalDate, Long> byDay = paid.stream().collect(Collectors.groupingBy(Booking::getBookingDate,
                TreeMap::new, Collectors.summingLong(Booking::getTotalPrice)));
        List<Map<String, Object>> revenueByDay = new ArrayList<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            revenueByDay.add(Map.of("date", d.toString(), "revenue", byDay.getOrDefault(d, 0L)));
        }

        Map<Enums.SportType, long[]> bySport = new EnumMap<>(Enums.SportType.class);
        for (Booking b : paid) {
            long[] acc = bySport.computeIfAbsent(b.getCourt().getSportType(), k -> new long[2]);
            acc[0] += b.getTotalPrice();
            acc[1]++;
        }
        List<Map<String, Object>> revenueBySport = bySport.entrySet().stream()
                .map(e -> Map.<String, Object>of("sport", e.getKey().name(), "label", e.getKey().getLabel(),
                        "revenue", e.getValue()[0], "bookings", e.getValue()[1]))
                .toList();

        Map<Long, List<Booking>> byCourt = paid.stream().collect(Collectors.groupingBy(b -> b.getCourt().getId()));
        List<Map<String, Object>> topCourts = byCourt.values().stream()
                .map(list -> {
                    Court c = list.get(0).getCourt();
                    return Map.<String, Object>of("courtId", c.getId(), "court", c.getName(),
                            "venue", c.getVenue().getName(),
                            "revenue", list.stream().mapToLong(Booking::getTotalPrice).sum(),
                            "bookings", list.size());
                })
                .sorted((a, b) -> Long.compare(((Number) b.get("revenue")).longValue(),
                        ((Number) a.get("revenue")).longValue()))
                .limit(5).toList();

        // Số giờ đã đặt theo khung giờ bắt đầu → biết giờ nào đông khách
        Map<Integer, Long> hourCount = new TreeMap<>();
        for (Booking b : paid) {
            for (int h = b.getStartTime().getHour(); h < b.getEndTime().getHour(); h++) hourCount.merge(h, 1L, Long::sum);
        }
        List<Map<String, Object>> bookingsByHour = hourCount.entrySet().stream()
                .map(e -> Map.<String, Object>of("hour", e.getKey(), "count", e.getValue())).toList();

        // Tỷ lệ lấp đầy = giờ đã bán / tổng giờ mở cửa của các sân đang hoạt động
        long days = ChronoUnit.DAYS.between(f, t) + 1;
        long capacityHours = 0;
        for (Venue v : venueRepository.findAllById(venueIds)) {
            long openHours = Duration.between(v.getOpenTime(), v.getCloseTime()).toHours();
            long courts = v.getCourts().stream().filter(Court::isActive).count();
            capacityHours += openHours * courts * days;
        }
        long bookedHours = paid.stream()
                .mapToLong(b -> Duration.between(b.getStartTime(), b.getEndTime()).toHours()).sum();
        double occupancy = capacityHours == 0 ? 0 : Math.round(bookedHours * 1000.0 / capacityHours) / 10.0;

        return new StatsDto(f, t, revenue, all.size(), paid.size(), cancelled, occupancy,
                revenueByDay, revenueBySport, topCourts, bookingsByHour);
    }
}
