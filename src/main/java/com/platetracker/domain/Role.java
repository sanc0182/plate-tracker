package com.platetracker.domain;

public enum Role {
    /** Super user: full CRUD on plates, employees, departments, users, plus the full log. */
    ADMIN,
    /** Employee user: can only take and return plates. */
    EMPLOYEE
}
