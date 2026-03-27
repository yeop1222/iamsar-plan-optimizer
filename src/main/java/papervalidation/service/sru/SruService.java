package papervalidation.service.sru;

import org.springframework.stereotype.Service;
import papervalidation.domain.sru.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SruService {

    private final Map<String, Sru> sruMap = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    private final SweepWidthTable sweepWidthTable;
    private final EnvironmentService environmentService;

    public SruService(SweepWidthTable sweepWidthTable, EnvironmentService environmentService) {
        this.sweepWidthTable = sweepWidthTable;
        this.environmentService = environmentService;
    }

    public Sru register(Sru sru) {
        String id = "sru-" + counter.incrementAndGet();
        sru.setId(id);
        if (sru.getType().isSurface()) sru.setAltitude(0);
        recalculate(sru);
        sruMap.put(id, sru);
        return sru;
    }

    public List<Sru> getAll() {
        return new ArrayList<>(sruMap.values());
    }

    public Sru getById(String id) {
        return sruMap.get(id);
    }

    public Sru update(String id, Sru sru) {
        sru.setId(id);
        if (sru.getType().isSurface()) sru.setAltitude(0);
        recalculate(sru);
        sruMap.put(id, sru);
        return sru;
    }

    public void delete(String id) {
        sruMap.remove(id);
    }

    /**
     * Recalculate W_0, f_v, f_w, f_f, W_corr, Z for a single SRU
     * using current global environment + search conditions.
     */
    public void recalculate(Sru sru) {
        EnvironmentCondition env = environmentService.getEnvironment();
        SearchCondition sc = environmentService.getSearchCondition();

        double w0 = sweepWidthTable.lookupW0(sru.getType(), sru.getAltitude(),
                env.getVisibility(), sc.getCategory(), sc.getObjectSize());
        double fv = sweepWidthTable.lookupVelocityFactor(sru.getType(), sru.getSearchSpeed(),
                sc.getCategory(), sc.getObjectSize());
        double fw = sweepWidthTable.weatherFactor(sc.getCategory(), sc.getObjectSize(),
                env.getWindSpeed(), env.getWaveHeight());
        double ff = sru.isFatigue() ? 0.9 : 1.0;

        sru.setUncorrectedSweepWidth(w0);
        sru.setVelocityFactor(fv);
        sru.setWeatherFactor(fw);
        sru.setFatigueFactor(ff);
        sru.setCalculatedSweepWidth(w0 * fv * fw * ff);

        double wCorr = sru.getEffectiveSweepWidth();
        sru.setSearchEffort(wCorr * sru.getSearchSpeed() * sru.getEndurance());
    }

    /** Recalculate all SRUs (after environment or search condition change) */
    public void recalculateAll() {
        sruMap.values().forEach(this::recalculate);
    }
}
