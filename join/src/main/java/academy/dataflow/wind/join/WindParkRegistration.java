package academy.dataflow.wind.join;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Master data for one wind park, keyed by the park id in
 * {@code nordwind.assets.public.park-registry.state}.
 *
 * @param windParkId unique park id, e.g. {@code arkona}
 * @param name       display name, e.g. {@code Arkona}
 * @param operator   the operating company
 * @param sea        {@code NORTH_SEA} or {@code BALTIC_SEA}
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record WindParkRegistration(
        String windParkId,
        String name,
        String operator,
        String sea) {
}
