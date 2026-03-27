package papervalidation.domain.datum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatumSet {
    private DatumType type;
    private List<DatumPoint> points = new ArrayList<>();
}
