package vn.sportbooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.sportbooking.entity.Enums;
import vn.sportbooking.entity.Venue;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    /** Tìm cụm sân theo môn, quận và từ khoá (tham số null = bỏ qua điều kiện đó). */
    @Query("""
            select distinct v from Venue v
            left join v.courts c
            where v.active = true
              and (:district is null or v.district = :district)
              and (:sport is null or (c.sportType = :sport and c.active = true))
              and (:keyword is null
                   or lower(v.name) like lower(concat('%', :keyword, '%'))
                   or lower(v.address) like lower(concat('%', :keyword, '%')))
            order by v.name
            """)
    List<Venue> search(@Param("sport") Enums.SportType sport,
                       @Param("district") String district,
                       @Param("keyword") String keyword);

    List<Venue> findByOwnerIdOrderByNameAsc(Long ownerId);

    @Query("select distinct v.district from Venue v where v.active = true order by v.district")
    List<String> findDistricts();
}
