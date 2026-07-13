package com.example.chatbot.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Department-scoped access (precursor to Step 5 SSO): the list of valid
 * departments comes from configuration; every upload and question is scoped
 * to exactly one of them. With SSO, the department will come from the JWT's
 * group claims instead of a request parameter.
 */
@Component
public class Departments {

    private final List<String> names;

    public Departments(@Value("${chatbot.departments:General,HR,Engineering,Finance}") List<String> names) {
        this.names = List.copyOf(names);
    }

    public List<String> list() {
        return names;
    }

    /** Returns the canonical department name, or throws if unknown. */
    public String validate(String department) {
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("Field 'department' is required. Valid values: " + names);
        }
        return names.stream()
                .filter(n -> n.equalsIgnoreCase(department.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown department '" + department + "'. Valid values: " + names));
    }
}
