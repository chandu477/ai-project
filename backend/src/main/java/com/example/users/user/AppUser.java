package com.example.users.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "monthly_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    @Column(name = "years_of_experience", nullable = false, precision = 3, scale = 1)
    private BigDecimal yearsOfExperience;

    protected AppUser() {
    }

    public AppUser(String username, BigDecimal monthlySalary, BigDecimal yearsOfExperience) {
        this.username = username;
        this.monthlySalary = monthlySalary;
        this.yearsOfExperience = yearsOfExperience;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public BigDecimal getMonthlySalary() {
        return monthlySalary;
    }

    public BigDecimal getYearsOfExperience() {
        return yearsOfExperience;
    }
}
