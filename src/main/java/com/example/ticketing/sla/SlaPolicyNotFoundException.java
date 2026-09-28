package com.example.ticketing.sla;

/**
 * Exception khi không tìm thấy SLA Policy.
 */
public class SlaPolicyNotFoundException extends RuntimeException {

    public SlaPolicyNotFoundException(Long id) {
        super("Không tìm thấy SLA Policy với ID: " + id);
    }

    public SlaPolicyNotFoundException(String message) {
        super(message);
    }
}
