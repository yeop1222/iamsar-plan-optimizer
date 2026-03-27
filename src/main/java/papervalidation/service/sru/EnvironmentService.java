package papervalidation.service.sru;

import org.springframework.stereotype.Service;
import papervalidation.domain.sru.EnvironmentCondition;
import papervalidation.domain.sru.SearchCondition;

/**
 * Global environment + search object condition (shared across all SRUs).
 */
@Service
public class EnvironmentService {

    private volatile EnvironmentCondition environment = new EnvironmentCondition();
    private volatile SearchCondition searchCondition = new SearchCondition();

    public EnvironmentCondition getEnvironment() { return environment; }
    public void updateEnvironment(EnvironmentCondition c) { this.environment = c; }

    public SearchCondition getSearchCondition() { return searchCondition; }
    public void updateSearchCondition(SearchCondition c) { this.searchCondition = c; }
}
