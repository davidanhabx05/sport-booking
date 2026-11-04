package vn.sportbooking.service;

import org.springframework.stereotype.Service;
import vn.sportbooking.entity.Court;
import vn.sportbooking.entity.Venue;

import java.time.LocalTime;

/**
 * Tính tiền sân theo từng khung 1 giờ: giờ cao điểm dùng giá peak, còn lại dùng giá thường.
 */
@Service
public class PricingService {

    public long priceForSlot(Court court, LocalTime slotStart) {
        Venue venue = court.getVenue();
        if (venue.isPeak(slotStart) && court.getPeakPricePerHour() != null) {
            return court.getPeakPricePerHour();
        }
        return court.getPricePerHour();
    }

    public long totalPrice(Court court, LocalTime start, LocalTime end) {
        long total = 0;
        for (LocalTime t = start; t.isBefore(end); t = t.plusHours(1)) {
            total += priceForSlot(court, t);
        }
        return total;
    }
}
