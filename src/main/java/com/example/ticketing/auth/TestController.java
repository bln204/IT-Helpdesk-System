package com.example.ticketing.auth;

import com.example.ticketing.incident.Incident;
import com.example.ticketing.incident.IncidentRepository;
import com.example.ticketing.servicerequest.ServiceRequest;
import com.example.ticketing.servicerequest.ServiceRequestRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {
    private final PasswordEncoder passwordEncoder;
    private final IncidentRepository incidentRepository;
    private final ServiceRequestRepository serviceRequestRepository;

    public TestController(PasswordEncoder passwordEncoder, 
                          IncidentRepository incidentRepository,
                          ServiceRequestRepository serviceRequestRepository) {
        this.passwordEncoder = passwordEncoder;
        this.incidentRepository = incidentRepository;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    @GetMapping("/hash")
    public String generateHash(@RequestParam String password) {
        return passwordEncoder.encode(password);
    }

    @GetMapping("/db")
    public Map<String, Object> testDb() {
        Map<String, Object> result = new HashMap<>();
        try {
            long count = incidentRepository.count();
            result.put("success", true);
            result.put("incidentCount", count);
            result.put("message", "Database connected OK");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }
        return result;
    }

    @GetMapping("/incidents-simple")
    public Map<String, Object> testIncidents() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Incident> incidents = incidentRepository.findAll();
            result.put("success", true);
            result.put("count", incidents.size());
            
            if (!incidents.isEmpty()) {
                Incident first = incidents.get(0);
                Map<String, Object> sample = new HashMap<>();
                sample.put("id", first.getId());
                sample.put("title", first.getTitle());
                sample.put("assignedTo", first.getAssignedTo() != null ? first.getAssignedTo().getUsername() : null);
                sample.put("team", first.getTeam() != null ? first.getTeam().getName() : null);
                sample.put("status", first.getStatus() != null ? first.getStatus().name() : null);
                sample.put("priority", first.getPriority() != null ? first.getPriority().name() : null);
                result.put("firstIncident", sample);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("stackTrace", e.getStackTrace()[0].toString());
        }
        return result;
    }

    @GetMapping("/service-requests-simple")
    public Map<String, Object> testServiceRequests() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ServiceRequest> requests = serviceRequestRepository.findAll();
            result.put("success", true);
            result.put("count", requests.size());
            
            if (!requests.isEmpty()) {
                ServiceRequest first = requests.get(0);
                Map<String, Object> sample = new HashMap<>();
                sample.put("id", first.getId());
                sample.put("title", first.getTitle());
                sample.put("status", first.getStatus() != null ? first.getStatus().name() : null);
                result.put("firstRequest", sample);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("stackTrace", e.getStackTrace()[0].toString());
        }
        return result;
    }

    @DeleteMapping("/incident/{id}")
    public Map<String, Object> testDeleteIncident(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            // Check if incident exists
            if (!incidentRepository.existsById(id)) {
                result.put("success", false);
                result.put("message", "Incident " + id + " not found");
                return result;
            }
            
            // Try to delete
            incidentRepository.deleteById(id);
            result.put("success", true);
            result.put("message", "Deleted incident " + id);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("errorMsg", e.getMessage());
            result.put("rootCause", e.getCause() != null ? 
                e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() : "null");
            
            // Get first few stack trace elements
            StringBuilder st = new StringBuilder();
            for (int i = 0; i < Math.min(3, e.getStackTrace().length); i++) {
                st.append(e.getStackTrace()[i].toString()).append("\n");
            }
            result.put("stackTrace", st.toString());
        }
        return result;
    }
}
