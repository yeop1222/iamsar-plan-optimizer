package papervalidation.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import papervalidation.domain.sru.Sru;
import papervalidation.service.sru.SruService;

import java.util.List;

@RestController
@RequestMapping("/api/sru")
public class SruRestController {

    private final SruService sruService;

    public SruRestController(SruService sruService) {
        this.sruService = sruService;
    }

    @PostMapping
    public ResponseEntity<Sru> register(@RequestBody Sru sru) {
        return ResponseEntity.ok(sruService.register(sru));
    }

    @GetMapping
    public ResponseEntity<List<Sru>> getAll() {
        return ResponseEntity.ok(sruService.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sru> update(@PathVariable String id, @RequestBody Sru sru) {
        if (sruService.getById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(sruService.update(id, sru));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sruService.delete(id);
        return ResponseEntity.ok().build();
    }
}
