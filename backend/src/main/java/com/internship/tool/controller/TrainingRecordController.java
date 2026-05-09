package com.internship.tool.controller;
import org.springframework.http.ResponseEntity;

import com.internship.tool.entity.TrainingRecord;
import com.internship.tool.entity.AuditLog;
import com.internship.tool.repository.TrainingRecordRepository;
import com.internship.tool.repository.AuditLogRepository;

import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDateTime;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

@RestController
@RequestMapping("/api/training")
public class TrainingRecordController {

    private final TrainingRecordRepository repository;
    private final AuditLogRepository auditRepository;

    public TrainingRecordController(TrainingRecordRepository repository,
                                    AuditLogRepository auditRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    // ✅ GET ALL WITH PAGINATION (SAFE)
    @GetMapping
    public Page<TrainingRecord> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        // 🔥 HOTFIX: pagination safety
        if (page < 0) page = 0;
        if (size <= 0) size = 5;

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        Pageable pageable = PageRequest.of(page, size, sort);

        return repository.findAll(pageable);
    }

    // ✅ UPDATE (SAFE)
    @PutMapping("/{id}")
    public TrainingRecord update(@PathVariable Long id,
                                 @RequestBody TrainingRecord updated,
                                 @RequestParam String role) {

        // 🔥 HOTFIX: role null + validation
        if (role == null ||
                (!role.equals("ADMIN") && !role.equals("MANAGER"))) {
            throw new IllegalArgumentException("Access Denied");
        }

        if (updated == null) {
            throw new IllegalArgumentException("Invalid request body");
        }

        if (updated.getTitle() == null) {
            throw new IllegalArgumentException("Title required");
        }

        TrainingRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));

        String oldData = record.toString();

        record.setTitle(updated.getTitle());
        record.setDescription(updated.getDescription());
        record.setStatus(updated.getStatus());
        record.setPriority(updated.getPriority());
        record.setAssignedTo(updated.getAssignedTo());
        record.setDueDate(updated.getDueDate());
        record.setScore(updated.getScore());

        TrainingRecord saved = repository.save(record);

        AuditLog log = new AuditLog();
        log.setEntityType("TrainingRecord");
        log.setEntityId(id);
        log.setAction("UPDATE");
        log.setChangedBy(role);
        log.setChangedAt(LocalDateTime.now());
        log.setOldValue(oldData);
        log.setNewValue(saved.toString());

        auditRepository.save(log);

        return saved;
    }

    // ✅ DELETE (SAFE)
    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id,
                         @RequestParam String role) {

        // 🔥 HOTFIX: role null check
        if (role == null || !role.equals("ADMIN")) {
            throw new IllegalArgumentException("Only ADMIN can delete");
        }

        TrainingRecord record = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));

        String oldData = record.toString();

        record.setStatus("DELETED");
        repository.save(record);

        AuditLog log = new AuditLog();
        log.setEntityType("TrainingRecord");
        log.setEntityId(id);
        log.setAction("DELETE");
        log.setChangedBy(role);
        log.setChangedAt(LocalDateTime.now());
        log.setOldValue(oldData);
        log.setNewValue("DELETED");

        auditRepository.save(log);

        return "Record deleted successfully";
    }


    @GetMapping("/search")
    public List<TrainingRecord> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {

        if (q != null && !q.isEmpty()) {
            return repository.findByTitleContainingIgnoreCase(q);
        }

        if (status != null && !status.isEmpty()) {
            return repository.findByStatus(status);
        }

        return repository.findAll();
    }

    // ✅ STATS
    @GetMapping("/stats")
    public Map<String, Long> stats() {

        long total = repository.count();
        long completed = repository.findByStatus("COMPLETED").size();
        long pending = repository.findByStatus("PENDING").size();

        Map<String, Long> response = new HashMap<>();
        response.put("total", total);
        response.put("completed", completed);
        response.put("pending", pending);

        return response;
    }
    @PostMapping
    public TrainingRecord createTraining(@RequestBody TrainingRecord record) {
        return repository.save(record);
    }
    @GetMapping("/audit")
    public List<AuditLog> getAuditLogs() {
        return auditRepository.findAll();
    }
    private void sendEmail(String message) {
        System.out.println("📧 EMAIL: " + message);
    }
    // ✅ CSV EXPORT (SAFE)
    @GetMapping(value = "/export", produces = "text/plain")
    public ResponseEntity<byte[]> exportCSV() {

        List<TrainingRecord> records = repository.findAll();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(out);

        // Header
        writer.println("ID,Title,Status,Priority,DueDate");

        // Data
        for (TrainingRecord r : records) {
            writer.println(
                    r.getId() + "," +
                            (r.getTitle() != null ? r.getTitle() : "") + "," +
                            (r.getStatus() != null ? r.getStatus() : "") + "," +
                            (r.getPriority() != null ? r.getPriority() : "") + "," +
                            (r.getDueDate() != null ? r.getDueDate() : "")
            );
        }

        writer.flush();

        byte[] csvBytes = out.toByteArray();

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=training_report.csv")
                .header("Content-Type", "text/csv")
                .body(csvBytes);
    }
    @PutMapping("/{id}/status")
    public TrainingRecord updateStatus(@PathVariable Long id, @RequestParam String status) {

        TrainingRecord record = repository.findById(id).orElseThrow();

        record.setStatus(status);
        TrainingRecord saved = repository.save(record);

        // 🔥 EMAIL SIMULATION (ADD THIS)
        System.out.println("📧 EMAIL: Training ID " + id + " status updated to " + status);

        return saved;
    }

}