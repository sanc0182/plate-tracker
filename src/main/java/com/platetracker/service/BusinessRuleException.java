package com.platetracker.service;

/** A rule of the plate process was broken (plate already out, wrong approver, ...). Message is safe to show to the user. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
