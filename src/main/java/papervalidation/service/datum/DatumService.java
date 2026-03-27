package papervalidation.service.datum;

import org.springframework.stereotype.Service;
import papervalidation.domain.datum.DatumSet;

/**
 * In-memory datum management.
 */
@Service
public class DatumService {

    private volatile DatumSet current;

    public DatumSet save(DatumSet datumSet) {
        this.current = datumSet;
        return current;
    }

    public DatumSet get() {
        return current;
    }

    public void clear() {
        this.current = null;
    }
}
